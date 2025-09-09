package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.*;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    public static final String POWER_CHECK_FREQUENCY_MINUTES = "1";
    public static final String POWER_CHECK_CRON_EXPRESSION = "0 0/" + POWER_CHECK_FREQUENCY_MINUTES + " * * * ?";
    public static final Duration AVERAGING_PERIOD = Duration.ofMinutes(1);
    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);
    public static final String DEHUMIDIDFIER_CODE = "DEH";

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final IndicationRepository indicationRepository;
    private final MessageService messageService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @Value("${mqtt.msgSavingEnabled}")
    private boolean msgSavingEnabled;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Value("${mqtt.msgSendingEnabled}")
    private boolean msgSendingEnabled;

    /*@EventListener(ApplicationReadyEvent.class)
    public void sendLastState() {
        Optional<Appliance> appliance = applianceRepository.findById(DEHUMIDIDFIER_CODE);
        if (appliance.isEmpty()) {
            LOGGER.error("Appliance {} was not found", DEHUMIDIDFIER_CODE);
            return;
        }
        LOGGER.info("Sending previous {} state", appliance.get().getDescription());
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        switchAppliance(appliance.orElse(null), localDateTime);
    }*/

    @Scheduled(cron = "*/3 * * * * *")
    public void updateApplianceActual() {
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
        Optional<Appliance> ac = applianceRepository.findById("AC");
        Optional<Appliance> deh = applianceRepository.findById("DEH");

        if (CollectionUtils.isNotEmpty(indications)) {

            Double averageTemp = null;
            Double averageAh = null;

            try {
                if (!ac.isEmpty()) {
                    List<String> referenceSensors = ac.get().getReferenceSensors();
                    calculateTrendAndSend(localDateTime, 1);
                    calculateTrendAndSend(localDateTime, 5);

                    averageTemp = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getTemperature()
                            .getValue().doubleValue()).average().orElseThrow();
                    ac.get().setActual(averageTemp);
                    try {
                        applianceRepository.save(ac.get());
                    } catch (ObjectOptimisticLockingFailureException e) {
                        ac = applianceRepository.findById("AC");
                        ac.get().setActual(averageAh);
                        applianceRepository.save(ac.get());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during updating AC actual", e);
            }

            try {
                if (!deh.isEmpty()) {
                    List<String> referenceSensors = deh.get().getReferenceSensors();
                    calculateTrendAndSend(localDateTime, 1);
                    calculateTrendAndSend(localDateTime, 5);

                    averageAh = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getAbsoluteHumidity()
                            .getValue().doubleValue()).average().orElseThrow();
                    deh.get().setActual(averageAh);
                    try {
                        applianceRepository.save(deh.get());
                    } catch (ObjectOptimisticLockingFailureException e) {
                        deh = applianceRepository.findById("DEH");
                        deh.get().setActual(averageAh);
                        applianceRepository.save(deh.get());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during updating AC actual", e);
            }

            appliance.setActual(averageAh);

            messageService.sendMessage(measurementTopic,
                    ("{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AVG\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.3f,"
                            + " \"ah\": %.3f}}}").formatted(averageTemp, averageAh));
        } else {
            try {
                applianceRepository.save(ac.get());
            } catch (ObjectOptimisticLockingFailureException e) {
                ac = applianceRepository.findById("AC");
                ac.get().setActual(null);
                applianceRepository.save(ac.get());
            }
            try {
                applianceRepository.save(deh.get());
            } catch (ObjectOptimisticLockingFailureException e) {
                deh = applianceRepository.findById("DEH");
                deh.get().setActual(null);
                applianceRepository.save(deh.get());
            }
        }
    }

    private void calculateTrendAndSend(LocalDateTime localDateTime, int minutes) {
        Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
        Double temperatureTrend = null;
        Double ahTrend = null;

        List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(
                List.of("HOME-AVG"), localDateTime.minusMinutes(minutes));
        try {
            Optional<IndicationV2> first = averages.stream().sorted(comparator.reversed()).findFirst();
            Optional<IndicationV2> second = averages.stream().sorted(comparator).findFirst();
            long secondsDifference = Duration.between(second.get().getLocalTime(), first.get().getLocalTime()).toSeconds();
            if (secondsDifference < ((long) minutes * 60 * 0.85)) {
                return;
            }
            temperatureTrend = (first.get().getTemperature().getValue() - second.get().getTemperature().getValue()) / secondsDifference * 3600;
            ahTrend = (first.get().getAbsoluteHumidity().getValue() - second.get().getAbsoluteHumidity().getValue()) / secondsDifference * 3600;
        } catch (Exception e) {
        }

        if (temperatureTrend != null || ahTrend != null) {
            messageService.sendMessage(measurementTopic,
                    "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-TREND%d\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.3f, \"ah\": %.3f}}}".formatted(
                            minutes,
                            temperatureTrend, ahTrend));
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

    public List<Appliance> getAllAppliances() {
        return applianceRepository.findAll();
    }

    public Optional<Appliance> getApplianceByCode(String code) {
        return applianceRepository.findById(code);
    }

    public Appliance saveOrUpdateAppliance(Appliance appliance) {
        Appliance save = null;
        try {
            save = applianceRepository.save(appliance);
        } catch (ObjectOptimisticLockingFailureException e) {
            Appliance updatedAppliance = applianceRepository.findById(appliance.getCode()).orElseThrow();
            applianceRepository.save(updatedAppliance);
            LOGGER.info("OptimisticLockException handled");
        }
        return save;
    }

    private void switchAppliance(Appliance appliance, LocalDateTime localDateTime) {
        Long durationInMinutes = null;

        if (appliance.getSwitched() != null) {
            durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), localDateTime).toMinutes());
        }

        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

        saveAuxApplianceMeasurement(appliance, localDateTime);

        messageService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        messageService.sendMessage(measurementTopic,
                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-%s\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                        appliance.getCode(), appliance.getState() == ON ? (appliance.getCode().equals("DEH") ? 1 : 2) : 0));

    }

    private void saveAuxApplianceMeasurement(Appliance appliance, LocalDateTime localDateTime) {
        if (!msgSavingEnabled) {
            return;
        }
        double value = appliance.getState() == ON ? 10.0 : 0.0;
        LocalDateTime utc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Measurement humValue = new Measurement().setValue(value);
        try {
            indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("DEHUMIDIFIER").setLocalTime(localDateTime)
                            .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue))
                    .setMetar("setting: %.2f, hysteresis: %.2f".formatted(appliance.getSetting(), appliance.getHysteresis()));
            indicationRepository.save(
                    Indication.builder().inOut(InOut.IN).aggregationPeriod(AggregationPeriod.INSTANT).receivedUtc(utc).receivedLocal(localDateTime)
                            .indicationPlace("DEHUMIDIFIER").air(Air.builder().temp(Temp.builder().celsius(value).ah(value).build()).build()).build());
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }

}
