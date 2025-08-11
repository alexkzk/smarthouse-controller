package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.entity.*;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.*;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.utils.TempUtils.calculateRelativeHumidityV2;

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

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void sendLastState() {
        Appliance appliance = applianceRepository.findById("AC").orElseThrow();
        LOGGER.info("Sending previous {} state", appliance.getDescription());
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        switchAppliance(appliance, localDateTime);
    }

    @Scheduled(cron = "*/3 * * * * *")
    public void updateDisplayStatus() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        LocalDateTime averagingStartDateTime = localDateTime.minus(AVERAGING_PERIOD);
        Appliance appliance = applianceRepository.findById("AC").orElseThrow();
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(appliance.getReferenceSensors(),
                averagingStartDateTime);
        if (CollectionUtils.isNotEmpty(indications)) {
            Double humMasterBed = null;
            Double tMasterBed = null;
            Double humBed = null;
            Double tBed = null;
            try {
                humMasterBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getAbsoluteHumidity() != null && i.getAbsoluteHumidity().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("APT2107S-MB"))
                                .mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {
            }

            try {
                tMasterBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getTemperature() != null && i.getTemperature().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("APT2107S-MB"))
                                .mapToDouble(i -> i.getTemperature().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }

            try {
                humBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getAbsoluteHumidity() != null && i.getAbsoluteHumidity().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("APT2107S-B"))
                                .mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }

            try {
                tBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getTemperature() != null && i.getTemperature().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("APT2107S-B"))
                                .mapToDouble(i -> i.getTemperature().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }

            Double average = null;
            try {
                average = (humMasterBed != null && humBed != null)
                        ? (humMasterBed + humBed) / 2
                        : (humMasterBed != null ? humMasterBed : (humBed != null ? humBed : null));

            } catch (Exception e) {
                LOGGER.error("Error during calculating average temperature and absolute humidity", e);
            }

            Map<String, String> statusMap = Map.of(
                    "Relative Humidity",
                    appliance.getActual() != null ? String.format("%.2f", calculateRelativeHumidityV2(24.0, appliance.getActual())) + "%" : "N/A",
                    "Target humidity",
                    appliance.getSetting() != null ? String.format("%.2f", calculateRelativeHumidityV2(24.0, appliance.getSetting())) + "%" : "N/A",
                    "Hysteresis",
                    appliance.getHysteresis() != null ? String.format("%.2f g/m3", appliance.getHysteresis()) : "N/A",
                    "Locked",
                    String.valueOf(appliance.isLocked()),
                    "Locked Until",
                    String.valueOf(appliance.getLockedUntilUtc()),
                    "ON minutes",
                    appliance.getDurationOnMinutes() != null ? String.format("%.0f", appliance.getDurationOnMinutes()) : "N/A",
                    "OFF minutes",
                    appliance.getDurationOffMinutes() != null ? String.format("%.0f", appliance.getDurationOffMinutes()) : "N/A",
                    "Reference sensors",
                    appliance.getReferenceSensors() != null ? String.valueOf(appliance.getReferenceSensors()) : "N/A",
                    "Master Bedroom",
                    (tMasterBed != null ? String.format("%.2f°C", tMasterBed) : "N/A") + "/" + (humMasterBed != null ? String.format("%.2f%%",
                            calculateRelativeHumidityV2(24.0, humMasterBed)) : "N/A"),
                    "Small Bedroom",
                    (tBed != null ? String.format("%.2f°C", tBed) : "N/A") + "/" + (humBed != null ? String.format("%.2f%%",
                            calculateRelativeHumidityV2(24.0, humBed)) : "N/A")
            );
            Map<String, String> displayStatus = new HashMap<>(statusMap);
            displayStatus.put("Absolute Humidity",  String.format("%.2f g/m3", appliance.getActual()));

            appliance.setActual(average);
            appliance.setDisplayStatus(displayStatus);

            if (msgSavingEnabled) {
                try {
                    applianceRepository.save(appliance);
                } catch (ObjectOptimisticLockingFailureException e) {
                    Appliance updatedAppliance = applianceRepository.findById("AC").orElseThrow();
                    updatedAppliance.setActual(average);
                    updatedAppliance.setDisplayStatus(displayStatus);
                    applianceRepository.save(updatedAppliance);
                    LOGGER.info("OptimisticLockException handled");
                }
            }
        } else {
            appliance.setActual(null);
            if (msgSavingEnabled) {
                try {
                    applianceRepository.save(appliance);
                } catch (ObjectOptimisticLockingFailureException e) {
                    Appliance updatedAppliance = applianceRepository.findById("AC").orElseThrow();
                    updatedAppliance.setActual(null);
                    applianceRepository.save(updatedAppliance);
                    LOGGER.info("OptimisticLockException handled");
                }
            }
        }


    }

    @Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControl() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        LocalDateTime utcLocalDateTime = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();

        String applianceCode = "AC";
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        if (appliance.getActual() == null) {
            appliance.setState(OFF, localDateTime);
            appliance.setActual(null);
            LOGGER.info("Power control method executed, indications were empty");
        } else {
            try {
                Double ah = appliance.getActual();
                LOGGER.info("Power control method executed, ah was: \u001B[34m{}\u001B[0m, the appliance's setting: {}, hysteresis: {}",
                        ah, appliance.getSetting(), appliance.getHysteresis());

                if (appliance.getLockedUntilUtc() != null && utcLocalDateTime.isAfter(appliance.getLockedUntilUtc())) {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                    LOGGER.info("Appliance '{}' was unlocked", appliance.getDescription());
                }

                if (!appliance.isLocked()) {
                    if (ah > appliance.getSetting() + appliance.getHysteresis()) {
                        appliance.setState(ON, localDateTime);
                    } else if (ah < appliance.getSetting() - appliance.getHysteresis()) {
                        appliance.setState(OFF, localDateTime);
                    }
                } else {
                    LOGGER.info("Appliance {} is locked {}", appliance.getDescription(), appliance.getLockedUntilUtc() == null ?
                            "indefinitely" : "until " + appliance.getLockedUntilUtc());
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
        Appliance save = null;
        try {
            save = applianceRepository.save(appliance);
        } catch (ObjectOptimisticLockingFailureException e) {
            Appliance updatedAppliance = applianceRepository.findById(appliance.getCode()).orElseThrow();
            applianceRepository.save(updatedAppliance);
            LOGGER.info("OptimisticLockException handled");
        };
        return save;
    }

    private void switchAppliance(Appliance appliance, LocalDateTime localDateTime) {
        Long durationInMinutes = null;

        if (appliance.getSwitched() != null) {
            durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), localDateTime).toMinutes());
        }

        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

        if (msgSavingEnabled) {
            mqttService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                    .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));

            saveAuxApplianceMeasurement(appliance, localDateTime);
        }
    }

    private void saveAuxApplianceMeasurement(Appliance appliance, LocalDateTime localDateTime) {
        double value = appliance.getState() == ON ? 10.0 : 0.0;
        LocalDateTime utc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Measurement humValue = new Measurement().setValue(value);
        try {
            if (msgSavingEnabled) {
                indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("DEHUMIDIFIER").setLocalTime(localDateTime)
                                .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue))
                        .setMetar("setting: %.2f, hysteresis: %.2f".formatted(appliance.getSetting(), appliance.getHysteresis()));
                indicationRepository.save(Indication.builder().inOut(InOut.IN).aggregationPeriod(AggregationPeriod.INSTANT).receivedUtc(utc).receivedLocal(localDateTime)
                        .indicationPlace("DEHUMIDIFIER").air(Air.builder().temp(Temp.builder().celsius(value).ah(value).build()).build()).build());
            }
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }

}
