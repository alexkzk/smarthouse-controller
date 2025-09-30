package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

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

    @Transactional
    public void powerControl(String applianceCode) {

        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();

        if (CollectionUtils.isNotEmpty(appliance.getReferenceSensors())) {
            LocalDateTime utcLocalDateTime = dateUtils.getUtcLocalDateTime();
            OptionalDouble averageOptional = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(appliance.getReferenceSensors(),
                            utcLocalDateTime.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes())), appliance.getMeasurementType())
                    .stream().mapToDouble(IndicationV3::getValue).average();

            if (averageOptional.isPresent()) {
                double average = averageOptional.getAsDouble();
                appliance.setActual(average);
                LOGGER.info("Power control method executed, average was: \u001B[34m{}\u001B[0m, the {} setting: {}, hysteresis: {}",
                        appliance.getDescription(), average, appliance.getSetting(), appliance.getHysteresis());

                //  Unlock if the lock is expired
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
                        boolean onCondition = average > appliance.getSetting() + appliance.getHysteresis();
                        boolean offCondition = average < appliance.getSetting() - appliance.getHysteresis();
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

                applianceRepository.save(appliance);
                Long durationInMinutes = null;
                if (appliance.getSwitched() != null) {
                    durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), utcLocalDateTime).toMinutes());
                }

                LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

                sendState(appliance, average);
            } else {
                LOGGER.info("Power control method executed, indications were empty");
            }
        } else {
            LOGGER.info("Reference sensors list is empty, skipping power control");
        }
    }

    public void sendState(Appliance appliance, double average) {
        if (appliance.getZigbee2MqttTopic() != null) {
            int brightness = average > 10 ? 255 : 160;
            String brightnessString = appliance.getState() == ON ? ", \"brightness\": %d".formatted(brightness) : "";
            messageService.sendMessage(appliance.getZigbee2MqttTopic(), ("{\"state\": \"%s\"" + brightnessString + "}").formatted(appliance.getState() == ON ? "on" : "off", 255));
        }
        messageService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));

        if (appliance.getCode().equals("DEH") || appliance.getCode().equals("AC")) {
            messageService.sendMessage(measurementTopic,
                    "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-%s\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                            appliance.getCode(), appliance.getState() == ON ? (appliance.getCode().equals("DEH") ? 1 : 2) : 0));
        }
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
