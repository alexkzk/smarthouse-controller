package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.Request;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.repository.ApplianceGroupRepository;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.utils.DateUtils.getUtc;
import static com.alexsoft.smarthouse.utils.DateUtils.toLocalDateTime;

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

    @EventListener
    @Transactional
    public void onHourChanged(HourChangedEvent event) {
        LocalDateTime utc = getUtc();
        applianceGroupRepository.findByTurnOffHoursIsNotNull().forEach(group -> Arrays.stream(group.getTurnOffHours().split(",")).forEach(turnOffHour -> {
            if (Integer.parseInt(turnOffHour) == event.getHour()) {
                applianceRepository.findAll().stream().filter(app -> app.getApplianceGroup().filter(gr -> gr.equals(group)).isPresent())
                        .forEach(app -> applianceFacade.toggle(app, ApplianceState.OFF, utc, "turn off hours setting", true));
            }
        }));
    }

    @Transactional
    public void powerControl(String applianceCode) {
        LocalDateTime utc = getUtc();
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();

        if (CollectionUtils.isNotEmpty(appliance.getReferenceSensors())) {
            OptionalDouble averageOptional = calculateAverage(appliance, utc).stream().mapToDouble(IndicationV3::getValue).average();

            if (averageOptional.isPresent()) {
                double average = averageOptional.getAsDouble();
                appliance.setActual(average);
                LOGGER.info("pwr-control for {} executed, avg: {}, setting: {}, hysteresis: {}",
                        appliance.getCode(), average, appliance.getSetting(), appliance.getHysteresis());

                sendAvgMessage(appliance, average, utc, toLocalDateTime(utc));
                checkLock(appliance, utc);

                if (!appliance.isLocked()) {

                    //  overwrite manual changed setting
                    Double scheduledSetting = appliance.determineScheduledSetting();
                    if (scheduledSetting != null && !Objects.equals(appliance.getScheduledSetting(), scheduledSetting)) {
                        appliance.setScheduledSetting(scheduledSetting);
                        appliance.setSetting(scheduledSetting);
                    }

                    //  control based ong avg setting
                    if (appliance.getSetting() != null) {
                        boolean onCondition = average > appliance.getSetting() + appliance.getHysteresis();
                        boolean offCondition = average < appliance.getSetting() - appliance.getHysteresis();
                        boolean acNeedsToBeTurnedOn;
                        if (toLocalDateTime(utc).getHour() > 22 || toLocalDateTime(utc).getHour() < 8) {
                            // night time
                            acNeedsToBeTurnedOn = appliance.getSwitched().isBefore(utc.minusMinutes(30));
                        } else {
                            // day time
                            acNeedsToBeTurnedOn = appliance.getSwitched().isBefore(utc.minusMinutes(20));
                        }
                        if (Boolean.TRUE.equals(appliance.getInverted()) ? !onCondition : onCondition) {
                            applianceFacade.toggle(appliance, ON, utc, "pwr-control", true);
                            return;
                        } else if (Boolean.TRUE.equals(appliance.getInverted()) ? !offCondition : offCondition) {
                            applianceFacade.toggle(appliance, OFF, utc, "pwr-control", true);
                            return;
                        } else if (appliance.getCode().equals("AC") && appliance.getState() == OFF && acNeedsToBeTurnedOn) {
                            LocalDateTime averageStart = utc.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes()));
                            OptionalDouble avgDehPowerConsumption = indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(List.of("lr-sp-dehumidifier"),
                                    averageStart, "power").stream().mapToDouble(IndicationV3::getValue).average();
                            if (avgDehPowerConsumption.isPresent() && avgDehPowerConsumption.getAsDouble() > 500) {
                                LOGGER.info("AC turned on based on DEH average consumption");
                                applianceFacade.toggle(appliance, ON , utc, "pwr-control", true);
                            }
                        }
                    }

                } else {
                    LOGGER.info("pwr-control Appliance {} is locked {}", appliance.getCode(), appliance.getLockedUntilUtc() == null ?
                            "indefinitely" : "until " + appliance.getLockedUntilUtc());
                }
                LOGGER.info("pwr-control {} is {} for {} minutes", appliance.getCode(), appliance.getFormattedState(),
                        calculateDurationSinceSwitch(appliance, utc));
            } else {
                appliance.setActual(null);
                LOGGER.info("pwr-control for {} executed, indications were empty", appliance.getCode());
            }

        } else {
            LOGGER.info("pwr-control for {} executed, reference sensors list is empty, skipping power control", appliance.getCode());
        }
        applianceFacade.toggle(appliance, appliance.getState(), utc, "pwr-control", true);
    }

    private List<IndicationV3> calculateAverage(Appliance appliance, LocalDateTime utc) {
        LocalDateTime averageStart = utc.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes()));
        return indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(appliance.getReferenceSensors(),
                averageStart, appliance.getMeasurementType());
    }

    private static Long calculateDurationSinceSwitch(Appliance appliance, LocalDateTime utc) {
        if (appliance.getSwitched() != null) {
            return Math.abs(Duration.between(appliance.getSwitched(), utc).toMinutes());
        }
        return null;
    }

    private static void checkLock(Appliance appliance, LocalDateTime utc) {
        if (appliance.getLockedUntilUtc() != null && utc.isAfter(appliance.getLockedUntilUtc())) {
            appliance.setLocked(false);
            appliance.setLockedUntilUtc(null);
            LOGGER.info("pwr-control '{}' has been unlocked", appliance.getDescription());
        }
    }

    private void sendAvgMessage(Appliance appliance, Double average, LocalDateTime utc, LocalDateTime now) {
        String metricType = appliance.getMetricType();
        if (metricType.equals("temp") || metricType.equals("humidity")) {
            String type = metricType.equals("humidity") ? "ah" : "temp";
            indicationServiceV3.save(IndicationV3.builder().locationId("HOME-AVG").localTime(now).utcTime(utc).publisherId("smarthouse-server").value(average)
                    .measurementType(type).value(average).build());
        }
    }

    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    public List<Appliance> getAllAppliances(String requesterId) {
        if (requesterId != null) {
            requestRepository.save(Request.builder().requesterId(requesterId).utcTime(getUtc()).build());
        }
        return applianceRepository.findAll();
    }

    @Transactional
    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        return applianceRepository.save(appliance);
    }
}


