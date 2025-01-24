package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.*;
import com.alexsoft.smarthouse.db.repository.ApplianceRepository;
import com.alexsoft.smarthouse.db.repository.IndicationRepository;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    public static final String POWER_CHECK_FREQUENCY_MINUTES = "1";
    public static final String POWER_CHECK_CRON_EXPRESSION = "0 0/" + POWER_CHECK_FREQUENCY_MINUTES + " * * * ?";
    public static final Duration AVERAGING_PERIOD = Duration.ofMinutes(5);
    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final IndicationRepository indicationRepository;
    private final MqttService mqttService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void sendLastState() {
        Appliance appliance = applianceRepository.findById("AC").orElseThrow();
        LOGGER.info("Sending previous {} state", appliance.getDescription());
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        switchAppliance(appliance, localDateTime);
    }

    @Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControl() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        LocalDateTime averagingStartDateTime = localDateTime.minus(AVERAGING_PERIOD);
        String applianceCode = "AC";
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(appliance.getReferenceSensors(), averagingStartDateTime);
        if (CollectionUtils.isEmpty(indications)) {
            appliance.setState(OFF, localDateTime);
            LOGGER.info("Power control method executed, indications were empty");
        } else {
            try {
                double ah = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getAbsoluteHumidity() != null && i.getAbsoluteHumidity().getValue() != null)
                                .mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
                LOGGER.info("Power control method executed, ah was: \u001B[34m{}\u001B[0m, the appliance's setting: {}, hysteresis: {}",
                        ah, appliance.getSetting(), appliance.getHysteresis());
                if (ah > appliance.getSetting() + appliance.getHysteresis()) {
                    appliance.setState(ON, localDateTime);
                } else if (ah < appliance.getSetting() - appliance.getHysteresis()) {
                    appliance.setState(OFF, localDateTime);
                }
            } catch (NoSuchElementException e) {
                LOGGER.warn("There were no values for calculating average absolute humidity");
            } catch (Exception e) {
                LOGGER.error("Error during calculating average absolute humidity", e);
            }
        }
        try {
            applianceRepository.save(appliance);
        } catch (ObjectOptimisticLockingFailureException e) {
            Appliance updatedAppliance = applianceRepository.findById(applianceCode).orElseThrow();
            updatedAppliance.setState(appliance.getState(), localDateTime);
            applianceRepository.save(updatedAppliance);
            LOGGER.info("OptimisticLockException handled");
        }
        switchAppliance(appliance, localDateTime);
    }

    // Fetch all appliances
    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    // Fetch a single appliance by code
    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    // Save or update an appliance
    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        return applianceRepository.save(appliance);
    }

    // Delete an appliance by code
    public void deleteAppliance(String code) {
        applianceRepository.deleteById(code);
    }

    // Update the state of an appliance
    public Appliance updateApplianceState(String code, ApplianceState state, LocalDateTime timestamp) {
        return applianceRepository.findById(code).map(appliance -> {
            appliance.setState(state, timestamp);
            return applianceRepository.save(appliance);
        }).orElseThrow(() -> new IllegalArgumentException("Appliance with code " + code + " not found"));
    }

    // Lock an appliance
    public Appliance lockAppliance(String code) {
        return applianceRepository.findById(code).map(appliance -> {
            appliance.setLocked(true);
            appliance.setLockedAt(LocalDateTime.now());
            return applianceRepository.save(appliance);
        }).orElseThrow(() -> new IllegalArgumentException("Appliance with code " + code + " not found"));
    }

    // Unlock an appliance
    public Appliance unlockAppliance(String code) {
        return applianceRepository.findById(code).map(appliance -> {
            appliance.setLocked(false);
            appliance.setLockedAt(null);
            return applianceRepository.save(appliance);
        }).orElseThrow(() -> new IllegalArgumentException("Appliance with code " + code + " not found"));
    }

    private void switchAppliance(Appliance appliance, LocalDateTime localDateTime) {
        Long durationInMinutes = null;

        if (appliance.getSwitched() != null) {
            durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), localDateTime).toMinutes());
        }

        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

        mqttService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));

        saveAuxApplianceMeasurement(appliance, localDateTime);
    }

    private void saveAuxApplianceMeasurement(Appliance appliance, LocalDateTime localDateTime) {
        double value = appliance.getState() == ON ? 10.0 : 0.0;
        LocalDateTime utc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Measurement humValue = new Measurement().setValue(value);
        try {
            indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("DEHUMIDIFIER").setLocalTime(localDateTime)
                    .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue))
                    .setMetar("setting: %.2f, hysteresis: %.2f".formatted(appliance.getSetting(), appliance.getHysteresis()));
            indicationRepository.save(Indication.builder().inOut(InOut.IN).aggregationPeriod(AggregationPeriod.INSTANT).receivedUtc(utc).receivedLocal(localDateTime)
                    .indicationPlace("DEHUMIDIFIER").air(Air.builder().temp(Temp.builder().celsius(value).ah(value).build()).build()).build());
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }

}
