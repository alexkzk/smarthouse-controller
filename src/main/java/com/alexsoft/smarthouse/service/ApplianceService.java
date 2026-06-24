package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.Request;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.util.DateUtils.getUtc;
import static com.alexsoft.smarthouse.util.DateUtils.toLocalDateTime;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    private final ApplianceRepository applianceRepository;
    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final ApplianceGroupRepository applianceGroupRepository;
    private final IndicationServiceV3 indicationServiceV3;
    private final RequestRepository requestRepository;
    private final ApplianceFacade applianceFacade;
    private final EventRepository eventRepository;
    private final ApartmentDetailsService apartmentDetailsService;

    @EventListener
    @Transactional
    public void onHourChanged(HourChangedEvent event) {
        LocalDateTime utc = getUtc();
        applianceGroupRepository.findByTurnOffHoursIsNotNull().forEach(group -> Arrays.stream(group.getTurnOffHours()
                .split(",")).forEach(turnOffHour -> {
            if (Integer.parseInt(turnOffHour) == event.getHour()) {
                List<Appliance> affected = applianceRepository.findAll().stream()
                        .filter(app -> app.getApplianceGroup().filter(gr -> gr.equals(group)).isPresent())
                        .toList();
                List<String> codes = affected.stream().map(Appliance::getCode).toList();
                eventRepository.save(Event.builder().utcTime(utc)
                        .type("group.%s.turn-off-hours.triggered".formatted(group.getCode()))
                        .data(Map.of("hour", event.getHour(), "appliances", codes)).build());
                affected.forEach(app -> applianceFacade.toggle(app, ApplianceState.OFF, utc, "turn off hours setting", true));
            }
        }));
    }

    @Transactional
    public void powerControl(String applianceCode) {
        LocalDateTime utc = getUtc();
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        appliance.setActual(null);
        checkScheduledSetting(appliance);
        checkLock(appliance, utc);

        OptionalDouble averageOptional = calculateAverage(appliance, utc);
        if (averageOptional.isPresent()) {
            double average = averageOptional.getAsDouble();
            appliance.setActual(average);
            saveAverageIndication(appliance, average, utc, toLocalDateTime(utc));

            if ("ah".equals(appliance.getMeasurementType()) && !CollectionUtils.isEmpty(appliance.getReferenceSensors())) {
                LocalDateTime averageStart = utc.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes()));
                Optional<Double> avgRhOpt = indicationRepositoryV3.findAvgValueByLocationIdInAndUtcTimeAfterAndMeasurementType(
                        appliance.getReferenceSensors(), averageStart, "rh");
                if (avgRhOpt.isPresent()) {
                    double avgRh = avgRhOpt.get();
                    appliance.setActualRh(avgRh);
                    String locationId = apartmentDetailsService.getLocationPrefix() + "-AVG";
                    indicationServiceV3.save(IndicationV3.builder().locationId(locationId).localTime(toLocalDateTime(utc)).utcTime(utc).publisherId("i7-4770k").value(avgRh)
                            .measurementType("rh").build());
                }
            }
            if (!appliance.isLocked() && appliance.getSetting() != null) {
                boolean onCondition = average > appliance.getSetting() + appliance.getHysteresisOn();
                boolean offCondition = average < appliance.getSetting() - appliance.getHysteresisOff();
                if (Boolean.TRUE.equals(appliance.getInverted()) ? offCondition : onCondition) {
                    logPwrControlDecision(appliance, ON, average, utc);
                    if (appliance.getState() != ON) {
                        applianceFacade.toggle(appliance, ON, utc, "pwr-control", true);
                    }
                } else if (Boolean.TRUE.equals(appliance.getInverted()) ? onCondition : offCondition) {
                    logPwrControlDecision(appliance, OFF, average, utc);
                    if (appliance.getState() != OFF) {
                        applianceFacade.toggle(appliance, OFF, utc, "pwr-control", true);
                    }
                }
            }
        } else if (!appliance.isLocked()) {
            applianceFacade.toggle(appliance, appliance.getState(), utc, "pwr-control", true);
        }

        applianceFacade.sendState(appliance);
    }

    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    public List<Appliance> getAllAppliances(String requesterId) {
        if (requesterId != null) {
            requestRepository.save(Request.builder().requesterId(requesterId).utcTime(getUtc()).build());
        } else {
            requestRepository.save(Request.builder().requesterId("UNKNOWN").utcTime(getUtc()).build());
        }
        return applianceRepository.findAll();
    }

    @Transactional
    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    @Transactional
    public Optional<Appliance> getApplianceByCodeReadOnly(String code) {
        return applianceRepository.findByIdForRead(code);
    }

    @Transactional
    public Optional<Appliance> getApplianceByZigbeeTopic(String topic) {
        return applianceRepository.findByZigbee2MqttTopicStartingWith(topic);
    }

    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        return applianceRepository.save(appliance);
    }

    private void checkScheduledSetting(Appliance appliance) {
        //  todo doublecheck this logic, seemed like didn't work well with AC heating mode
        Double scheduledSetting = appliance.determineScheduledSetting();
        if (scheduledSetting != null && !Objects.equals(appliance.getScheduledSetting(), scheduledSetting)) {
            appliance.setScheduledSetting(scheduledSetting);
            appliance.setSetting(scheduledSetting);
        }

    }

    private OptionalDouble calculateAverage(Appliance appliance, LocalDateTime utc) {
        if (CollectionUtils.isEmpty(appliance.getReferenceSensors())) {
            return OptionalDouble.empty();
        }
        LocalDateTime averageStart = utc.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes()));
        return indicationRepositoryV3.findAvgValueByLocationIdInAndUtcTimeAfterAndMeasurementType(
                        appliance.getReferenceSensors(), averageStart, appliance.getMeasurementType())
                .map(OptionalDouble::of).orElse(OptionalDouble.empty());
    }

    private void checkLock(Appliance appliance, LocalDateTime utc) {
        if (appliance.getLockedUntilUtc() != null && utc.isAfter(appliance.getLockedUntilUtc())) {
            LocalDateTime wasLockedUntil = appliance.getLockedUntilUtc();
            appliance.setLocked(false);
            appliance.setLockedUntilUtc(null);
            LOGGER.info("pwr-control '{}' has been unlocked", appliance.getDescription());
            eventRepository.save(Event.builder().utcTime(utc)
                    .type("lock.expired").device(appliance.getCode())
                    .data(Map.of("wasLockedUntil", wasLockedUntil.toString())).build());
        }
    }

    private void logPwrControlDecision(Appliance appliance, ApplianceState decision, double average, LocalDateTime utc) {
        String type = appliance.getState() != decision ? "pwr-control.trigger" : "pwr-control.check";
        eventRepository.save(Event.builder().utcTime(utc)
                .type(type).device(appliance.getCode())
                .data(Map.of("decision", decision.name().toLowerCase(), "avg", average, "setting", appliance.getSetting(), "hysteresisOn", appliance.getHysteresisOn(), "hysteresisOff", appliance.getHysteresisOff())).build());
    }

    private void saveAverageIndication(Appliance appliance, Double average, LocalDateTime utc, LocalDateTime now) {
        String metricType = appliance.getMetricType();
        if (metricType.equals("temp") || metricType.equals("humidity")) {
            String type = metricType.equals("humidity") ? "ah" : "temp";
            String locationId = apartmentDetailsService.getLocationPrefix() + "-AVG";
            indicationServiceV3.save(IndicationV3.builder().locationId(locationId).localTime(now).utcTime(utc).publisherId("i7-4770k").value(average)
                    .measurementType(type).value(average).build());
        }
    }
}


