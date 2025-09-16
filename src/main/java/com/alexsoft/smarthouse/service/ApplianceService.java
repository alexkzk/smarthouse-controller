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
import java.util.*;

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

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final MessageService messageService;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Scheduled(cron = "*/3 * * * * *")
    public void calculateAverages() {
        List<String> referenceSensors = List.of("HOME-MB","HOME-LR","HOME-B");
        LocalDateTime averagingStartDateTime = dateUtils.getLocalDateTime().minus(AVERAGING_PERIOD);
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(referenceSensors, averagingStartDateTime);
        Double averageTemp = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getTemperature()
                .getValue().doubleValue()).average().orElseThrow();
        Double averageAh = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getAbsoluteHumidity()
                .getValue().doubleValue()).average().orElseThrow();
        messageService.sendMessage(measurementTopic,
                ("{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AVG\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.3f,"
                        + " \"ah\": %.3f}}}").formatted(averageTemp, averageAh));
    }

    @Scheduled(cron = "*/3 * * * * *")
    public void calculateTrends() {
        calculateTrendAndSend(dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime()), 1);
        calculateTrendAndSend(dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime()), 5);
    }


    private void calculateTrendAndSend(LocalDateTime localDateTime, int minutes) {
        Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
        Double temperatureTrend;
        Double ahTrend;

        List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(
                List.of("HOME-AVG"), localDateTime.minusMinutes(minutes));
        Optional<IndicationV2> first = averages.stream().sorted(comparator.reversed()).findFirst();
        Optional<IndicationV2> second = averages.stream().sorted(comparator).findFirst();
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

    @Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControlAcAndDehumidifier() {
        powerControl("AC");
        powerControl("DEH");
    }

    public void powerControl(String applianceCode) {
        LocalDateTime utcLocalDateTime = dateUtils.getUtcLocalDateTime();
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(utcLocalDateTime);
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        controlPower(appliance, localDateTime, utcLocalDateTime);
        save(appliance, localDateTime);
        switchAppliance(appliance, localDateTime);
    }

    private void save(Appliance appliance, LocalDateTime localDateTime) {
        try {
            applianceRepository.save(appliance);
        } catch (ObjectOptimisticLockingFailureException e) {
            Appliance updatedAppliance = applianceRepository.findById(appliance.getCode()).orElseThrow();
            updatedAppliance.setState(appliance.getState(), localDateTime);
            applianceRepository.save(updatedAppliance);
            LOGGER.info("OptimisticLockException handled");
        }
    }

    private void controlPower(Appliance appliance, LocalDateTime localDateTime, LocalDateTime utcLocalDateTime) {
        Optional<IndicationV2> average = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(
                List.of("HOME-AVG"), localDateTime.minus(AVERAGING_PERIOD)).stream().sorted(
                Comparator.comparing(IndicationV2::getLocalTime)).findFirst();

        if (average.isPresent()) {
            Double actual = appliance.getActual(average.get());
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
                    if (actual > appliance.getSetting() + appliance.getHysteresis()) {
                        appliance.setState(ON, localDateTime);
                    } else if (actual < appliance.getSetting() - appliance.getHysteresis()) {
                        appliance.setState(OFF, localDateTime);
                    }
                }
            } else {
                LOGGER.info("Appliance {} is locked {}", appliance.getDescription(), appliance.getLockedUntilUtc() == null ?
                        "indefinitely" : "until " + appliance.getLockedUntilUtc());
            }
        } else {
            appliance.setState(OFF, localDateTime);
            LOGGER.info("Power control method executed, indications were empty");
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

        messageService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        messageService.sendMessage(measurementTopic,
                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-%s\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                        appliance.getCode(), appliance.getState() == ON ? (appliance.getCode().equals("DEH") ? 1 : 2) : 0));

    }

}
