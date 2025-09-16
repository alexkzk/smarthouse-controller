package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.service.MessageService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.alexsoft.smarthouse.service.ApplianceService.AVERAGING_PERIOD;
import static com.alexsoft.smarthouse.service.ApplianceService.POWER_CHECK_CRON_EXPRESSION;

@Service
@RequiredArgsConstructor
public class ScheduledService {

    private final ApplianceService applianceService;
    private final DateUtils dateUtils;
    private final MessageService messageService;
    private final IndicationRepositoryV2 indicationRepositoryV2;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControlAcAndDehumidifier() {
        applianceService.powerControl("AC");
        applianceService.powerControl("DEH");
    }

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
        applianceService.calculateTrendAndSend(dateUtils.getLocalDateTime(), 1);
        applianceService.calculateTrendAndSend(dateUtils.getLocalDateTime(), 5);
    }

}
