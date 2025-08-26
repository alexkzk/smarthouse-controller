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
    public static final String DEHUMIDIDFIER_CODE = "DEH";

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final IndicationRepository indicationRepository;
    private final MqttService mqttService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @Value("${mqtt.msgSavingEnabled}")
    private boolean msgSavingEnabled;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Value("${mqtt.msgSendingEnabled}")
    private boolean msgSendingEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void sendLastState() {
        Optional<Appliance> appliance = applianceRepository.findById(DEHUMIDIDFIER_CODE);
        if (appliance.isEmpty()) {
            LOGGER.error("Appliance {} was not found", DEHUMIDIDFIER_CODE);
            return;
        }
        LOGGER.info("Sending previous {} state", appliance.get().getDescription());
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        switchAppliance(appliance.orElse(null), localDateTime);
    }

    @Scheduled(cron = "*/3 * * * * *")
    public void updateDisplayStatus() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        LocalDateTime averagingStartDateTime = localDateTime.minus(AVERAGING_PERIOD);
        Optional<Appliance> applianceOptional = applianceRepository.findById(DEHUMIDIDFIER_CODE);
        if (applianceOptional.isEmpty()) {
           LOGGER.error("Appliance {} was not found", DEHUMIDIDFIER_CODE);
           return;
        }
        Appliance appliance = applianceOptional.get();
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(appliance.getReferenceSensors(),
                averagingStartDateTime);
        if (CollectionUtils.isNotEmpty(indications)) {
            Double humMasterBed = null;
            Double tMasterBed = null;
            Double humLr = null;
            Double tLr = null;
            try {
                humMasterBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getAbsoluteHumidity() != null && i.getAbsoluteHumidity().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("HOME-MB"))
                                .mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {
            }

            try {
                tMasterBed = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getTemperature() != null && i.getTemperature().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("HOME-MB"))
                                .mapToDouble(i -> i.getTemperature().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }

            try {
                humLr = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getAbsoluteHumidity() != null && i.getAbsoluteHumidity().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("HOME-LR"))   // todo remove hardcoded indication place
                                .mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }

            try {
                tLr = BigDecimal.valueOf(indications.stream().filter(Objects::nonNull)
                                .filter(i -> i.getTemperature() != null && i.getTemperature().getValue() != null)
                                .filter(i -> i.getIndicationPlace().equals("HOME-LR"))
                                .mapToDouble(i -> i.getTemperature().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (Exception e) {

            }
            Double averageAh = null;
            try {
                averageAh = (humMasterBed != null && humLr != null)
                        ? (humMasterBed + humLr) / 2
                        : (humMasterBed != null ? humMasterBed : (humLr != null ? humLr : null));

            } catch (Exception e) {
                LOGGER.error("Error during calculating averageAh temperature and absolute humidity", e);
            }

            try {
                Optional<Appliance> ac = applianceRepository.findById("AC");
                if (!ac.isEmpty()) {
                    List<String> referenceSensors = ac.get().getReferenceSensors();
                    List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(
                            List.of("HOME-AVG"), averagingStartDateTime);
                    Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
                    Double temperatureTrend = null;
                    Double ahTrend = null;

                    try {
                        temperatureTrend = averages.stream().sorted(comparator.reversed()).findFirst().get().getTemperature().getValue() - averages.stream().sorted(
                                comparator).findFirst().get().getTemperature().getValue();
                    } catch (Exception e) {}

                    try {
                        ahTrend = averages.stream().sorted(comparator.reversed()).findFirst().get().getAbsoluteHumidity().getValue() - averages.stream().sorted(
                                comparator).findFirst().get().getAbsoluteHumidity().getValue();
                    } catch (Exception e) {}

                    Double averageTemp = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getTemperature().getValue().doubleValue()).average().orElseThrow();
                    ac.get().setActual(averageTemp);
                    applianceRepository.save(ac.get());

                    if (msgSendingEnabled) {
                        mqttService.sendMessage(measurementTopic,
                                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AVG\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.2f, \"ah\": %.2f}}}".formatted(
                                        averageTemp, averageAh));
                        if (temperatureTrend != null || ahTrend != null) {
                            mqttService.sendMessage(measurementTopic,
                                    "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-TREND\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.2f, \"ah\": %.2f}}}".formatted(
                                            temperatureTrend, ahTrend));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during updating AC actual", e);
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
                    (tLr != null ? String.format("%.2f°C", tLr) : "N/A") + "/" + (humLr != null ? String.format("%.2f%%",
                            calculateRelativeHumidityV2(24.0, humLr)) : "N/A")
            );
            Map<String, String> displayStatus = new HashMap<>(statusMap);
            displayStatus.put("Absolute Humidity",  String.format("%.2f g/m3", appliance.getActual()));

            appliance.setActual(averageAh);
            appliance.setDisplayStatus(displayStatus);

            if (msgSavingEnabled) {
                try {
                    applianceRepository.save(appliance);
                } catch (ObjectOptimisticLockingFailureException e) {
                    Appliance updatedAppliance = applianceRepository.findById(DEHUMIDIDFIER_CODE).orElseThrow();
                    updatedAppliance.setActual(averageAh);
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
                    Appliance updatedAppliance = applianceRepository.findById(DEHUMIDIDFIER_CODE).orElseThrow();
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

        Appliance dehumidifier = applianceRepository.findById(DEHUMIDIDFIER_CODE).orElseThrow();
        Appliance ac = applianceRepository.findById("AC").orElseThrow();

        controlPower(dehumidifier, localDateTime, utcLocalDateTime);
        controlPower(ac, localDateTime, utcLocalDateTime);

        save(dehumidifier, localDateTime);
        save(ac, localDateTime);
        switchAppliance(dehumidifier, localDateTime);
        switchAppliance(ac, localDateTime);
    }

    private void save(Appliance dehumidifier, LocalDateTime localDateTime) {
        try {
            applianceRepository.save(dehumidifier);
        } catch (ObjectOptimisticLockingFailureException e) {
            Appliance updatedAppliance = applianceRepository.findById(DEHUMIDIDFIER_CODE).orElseThrow();
            updatedAppliance.setState(dehumidifier.getState(), localDateTime);
            applianceRepository.save(updatedAppliance);
            LOGGER.info("OptimisticLockException handled");
        }
    }

    private static void controlPower(Appliance appliance, LocalDateTime localDateTime, LocalDateTime utcLocalDateTime) {
        if (appliance.getActual() == null) {
            appliance.setState(OFF, localDateTime);
            appliance.setActual(null);
            LOGGER.info("Power control method executed, indications were empty");
        } else {
            try {
                Double actual = appliance.getActual();
                LOGGER.info("Power control method executed, actual was: \u001B[34m{}\u001B[0m, the {} setting: {}, hysteresis: {}",
                        appliance.getDescription(), actual, appliance.getSetting(), appliance.getHysteresis());

                if (appliance.getLockedUntilUtc() != null && utcLocalDateTime.isAfter(appliance.getLockedUntilUtc())) {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                    LOGGER.info("Appliance '{}' was unlocked", appliance.getDescription());
                }

                if (!appliance.isLocked()) {
                    if (actual > appliance.getSetting() + appliance.getHysteresis()) {
                        appliance.setState(ON, localDateTime);
                    } else if (actual < appliance.getSetting() - appliance.getHysteresis()) {
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

            saveAuxApplianceMeasurement(appliance, localDateTime);
        }

        if (msgSendingEnabled) {
            mqttService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                    .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
            mqttService.sendMessage(measurementTopic,
                    "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-%s\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                            appliance.getCode(), appliance.getState() == ON ? (appliance.getCode().equals("DEH") ? 1 : 2) : 0));
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
