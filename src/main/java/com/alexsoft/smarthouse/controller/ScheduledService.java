package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.model.ApplianceSwitchEvent;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.service.MessageService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.service.ApplianceService.AVERAGING_PERIOD;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledService {

    private final ApplianceService applianceService;
    private final DateUtils dateUtils;
    private final MessageService messageService;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final ApplianceRepository applianceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    /*@Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControlAcAndDehumidifier() {
        applianceService.powerControl("AC", null);
        applianceService.powerControl("DEH", null);
    }*/

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        applianceService.getAllAppliances().forEach(applianceService::sendState);
    }


    @Scheduled(cron = "0 * * * * *")
    public void currentStateSender() {
        applianceService.getAllAppliances().forEach(applianceService::sendState);
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduleUpdater() {
        Appliance ac = applianceRepository.findById("AC").get();
        if (!ac.isLocked()) {
            Double scheduledSetting = ac.determineScheduledSetting();
            if (scheduledSetting != null && !Objects.equals(ac.getScheduledSetting(), scheduledSetting)) {
                ac.setScheduledSetting(scheduledSetting);
                ac.setSetting(scheduledSetting);
            }
        }
        applianceService.saveAndSendState(ac);
    }


    @Scheduled(cron = "*/15 * * * * *")
    @Transactional(readOnly = false)
    public void averageCalculator() {
        List<String> referenceSensors = List.of("HOME-MB","HOME-LR","HOME-B");
        LocalDateTime averagingStartDateTime = dateUtils.getLocalDateTime().minus(AVERAGING_PERIOD);
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(referenceSensors, averagingStartDateTime);
        if (indications.isEmpty()){
            return;
        }
        Double averageTemp = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getTemperature()
                .getValue().doubleValue()).average().orElseThrow();
        Double averageAh = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getAbsoluteHumidity()
                .getValue().doubleValue()).average().orElseThrow();
        applianceRepository.updateActualByCode("AC", BigDecimal.valueOf(averageTemp).setScale(3, RoundingMode.HALF_UP).doubleValue());
        applianceRepository.updateActualByCode("DEH", BigDecimal.valueOf(averageAh).setScale(3, RoundingMode.HALF_UP).doubleValue());
        messageService.sendMessage(measurementTopic,
                ("{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AVG\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.3f,"
                        + " \"ah\": %.3f}}}").formatted(averageTemp, averageAh));

        Appliance ac = applianceRepository.findById("AC").get();
        if (ac.getSetting() != null && ac.getHysteresis() != null) {
            if (averageTemp > ac.getSetting() + ac.getHysteresis()) {
//                if (ac.getState() == ApplianceState.OFF) {
                    eventPublisher.publishEvent(new ApplianceSwitchEvent("AC", ApplianceState.ON));
//                }
            } else if (averageTemp < ac.getSetting() - ac.getHysteresis()) {
//                if (ac.getState() == ApplianceState.ON) {
                    eventPublisher.publishEvent(new ApplianceSwitchEvent("AC", ApplianceState.OFF));
//                }
            }
        }
        messageService.sendMessage(measurementTopic,
                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AC\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                        ac.getState() == ON ? 2 : 0));


        Appliance deh = applianceRepository.findById("DEH").get();
        if (deh.getSetting() != null && deh.getHysteresis() != null) {
            if (averageAh > deh.getSetting() + deh.getHysteresis()) {
                eventPublisher.publishEvent(new ApplianceSwitchEvent("DEH", ApplianceState.ON));
            } else if (averageTemp < deh.getSetting() - deh.getHysteresis()) {
                eventPublisher.publishEvent(new ApplianceSwitchEvent("DEH", ApplianceState.OFF));
            }
        }
        messageService.sendMessage(measurementTopic,
                "{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-DEH\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %d}}}".formatted(
                        ac.getState() == ON ? 1 : 0));
    }

    @Scheduled(cron = "*/3 * * * * *")
    public void trendsCalculator() {
        calculateTrendAndSend(dateUtils.getLocalDateTime(), 1);
        calculateTrendAndSend(dateUtils.getLocalDateTime(), 5);
    }


    public void calculateTrendAndSend(LocalDateTime localDateTime, int minutes) {
        Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
        Double temperatureTrend;
        Double ahTrend;

        List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(
                List.of("HOME-AVG"), localDateTime.minusMinutes(minutes));
        Optional<IndicationV2> first = averages.stream().sorted(comparator.reversed()).findFirst();
        Optional<IndicationV2> second = averages.stream().sorted(comparator).findFirst();
        if (!first.isPresent() || !second.isPresent()) {
            log.info("No trend calculated, no averages found");
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

}
