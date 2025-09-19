package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.model.ApplianceSwitchEvent;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    public static final Duration AVERAGING_PERIOD = Duration.ofMinutes(1);
    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    private final MessageService messageService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @EventListener
    @Transactional
    public void powerControl(ApplianceSwitchEvent event) {
        LocalDateTime utcLocalDateTime = dateUtils.getUtcLocalDateTime();
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(utcLocalDateTime);
        Appliance appliance = applianceRepository.findById(event.getApplianceCode()).orElseThrow();
        /*LOGGER.info("Power control method executed, actual was: \u001B[34m{}\u001B[0m, the {} setting: {}, hysteresis: {}",
                            appliance.getDescription(), actual, appliance.getSetting(), appliance.getHysteresis());*/
        if (appliance.getLockedUntilUtc() != null && utcLocalDateTime.isAfter(appliance.getLockedUntilUtc())) {
            appliance.setLocked(false);
            appliance.setLockedUntilUtc(null);
            LOGGER.info("Appliance '{}' was unlocked", appliance.getDescription());
        }

        if (!appliance.isLocked()) {
            appliance.setState(event.getState(), localDateTime);
        } else {
            LOGGER.info("Appliance {} is locked {}", appliance.getDescription(), appliance.getLockedUntilUtc() == null ?
                    "indefinitely" : "until " + appliance.getLockedUntilUtc());
        }
        saveAndSendState(appliance);

        LOGGER.info("{} is {}", appliance.getDescription(), appliance.getFormattedState());
    }

    public void sendState(Appliance appliance) {
        messageService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
    }

    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        return saveAndSendState(appliance);
    }

    @Transactional
    public Appliance saveAndSendState(Appliance appliance) {
        Appliance saved = applianceRepository.save(appliance);
        sendState(saved);
        return saved;
    }

}
