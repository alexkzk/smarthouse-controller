package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final MessageService messageService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;
    private final IndicationRepositoryV3 indicationRepositoryV3;

    @Value("${mqtt.topic}")
    private String measurementTopic;


    public void calculateTrendAndSend(LocalDateTime localDateTime, int minutes) {
        Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
        Double temperatureTrend;
        Double ahTrend;

        List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndUtcTimeAfter(
                List.of("HOME-AVG"), localDateTime.minusMinutes(minutes));
        Optional<IndicationV2> first = averages.stream().sorted(comparator.reversed()).findFirst();
        Optional<IndicationV2> second = averages.stream().sorted(comparator).findFirst();
        if (!first.isPresent() || !second.isPresent()) {
            LOGGER.info("No trend calculated, no averages found");
            return;
        }
        long secondsDifference = Duration.between(second.get().getLocalTime(), first.get().getLocalTime()).toSeconds();
        if (secondsDifference < ((long) minutes * 60 * 0.85)) {
            return;
        }
        temperatureTrend = (first.get().getTemperature().getValue() - second.get().getTemperature().getValue()) / secondsDifference * 3600;
        ahTrend = (first.get().getAbsoluteHumidity().getValue() - second.get().getAbsoluteHumidity().getValue()) / secondsDifference * 3600;

        if (temperatureTrend != null || ahTrend != null) {
            messageService.sendMessage(measurementTopic,
                    ("{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-TREND%d\", \"inOut\": \"IN\", \"air\":"
                            + " {\"temp\": {\"celsius\": %.3f, \"ah\": %.3f}}}").formatted(minutes, temperatureTrend, ahTrend));
        }
    }

    @Transactional
    public void powerControl(String applianceCode) {
        LocalDateTime utcLocalDateTime = dateUtils.getUtcLocalDateTime();
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();

        if (CollectionUtils.isNotEmpty(appliance.getReferenceSensors())) {
            OptionalDouble average = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(appliance.getReferenceSensors(),
                            utcLocalDateTime.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes())), appliance.getMeasurementType())
                    .stream().mapToDouble(IndicationV3::getValue).average();
            if (average.isPresent()) {
                double actual = average.getAsDouble();
                LOGGER.info("Power control method executed, actual was: \u001B[34m{}\u001B[0m, the {} setting: {}, hysteresis: {}",
                        appliance.getDescription(), actual, appliance.getSetting(), appliance.getHysteresis());
                if (appliance.getLockedUntilUtc() != null && utcLocalDateTime.isAfter(appliance.getLockedUntilUtc())) {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                    LOGGER.info("Appliance '{}' was unlocked", appliance.getDescription());
                }

                if (!appliance.isLocked()) {
                    Double scheduledSetting = appliance.determineScheduledSetting();
                    if (scheduledSetting != null && !Objects.equals(appliance.getScheduledSetting(), scheduledSetting)) {
                        appliance.setScheduledSetting(scheduledSetting);
                        appliance.setSetting(scheduledSetting);
                    }
                    if (appliance.getSetting() != null) {
                        boolean onCondition = actual > appliance.getSetting() + appliance.getHysteresis();
                        boolean offCondition = actual < appliance.getSetting() - appliance.getHysteresis();
                        if (Boolean.TRUE.equals(appliance.getInverted()) ? !onCondition : onCondition) {
                            appliance.setState(ON, utcLocalDateTime);
                            if (appliance.getMinimumOnCycleMinutes() != null) {
                                appliance.setLocked(true);
                                appliance.setLockedUntilUtc(utcLocalDateTime.plusMinutes(appliance.getMinimumOnCycleMinutes()));
                            }
                        } else if (Boolean.TRUE.equals(appliance.getInverted()) ? !offCondition : offCondition) {
                            appliance.setState(OFF, utcLocalDateTime);
                            if (appliance.getMinimumOffCycleMinutes() != null) {
                                appliance.setLocked(true);
                                appliance.setLockedUntilUtc(utcLocalDateTime.plusMinutes(appliance.getMinimumOffCycleMinutes()));
                            }
                        }
                    }
                } else {
                    LOGGER.info("Appliance {} is locked {}", appliance.getDescription(), appliance.getLockedUntilUtc() == null ?
                            "indefinitely" : "until " + appliance.getLockedUntilUtc());
                }
            } else {
                appliance.setState(OFF, utcLocalDateTime);
                LOGGER.info("Power control method executed, indications were empty");
            }
        }

        save(appliance);
        Long durationInMinutes = null;
        if (appliance.getSwitched() != null) {
            durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), utcLocalDateTime).toMinutes());
        }

        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

        if (appliance.getZigbee2MqttTopic() == null) {
            messageService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                    .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        } else {
            messageService.sendMessage(appliance.getZigbee2MqttTopic(), "{\"state\": \"%s\"}".formatted(appliance.getState() == ON ? "on" : "off"));
        }
        messageService.sendMessage(measurementTopic,
                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-%s\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                        appliance.getCode(), appliance.getState() == ON ? (appliance.getCode().equals("DEH") ? 1 : 2) : 0));
    }

    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        return applianceRepository.save(appliance);
    }

    private void save(Appliance appliance) {
        applianceRepository.save(appliance);
    }

}
