package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledService {

    public static final Duration AVG_DURATION_MINUTES = Duration.ofMinutes(1);
    private final ApplianceService applianceService;
    private final DateUtils dateUtils;
    private final MessageService messageService;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final ApplianceRepository applianceRepository;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Scheduled(cron = "0 0/1 * * * ?")
    @Transactional
    public void powerControl() {
        applianceService.getAllAppliances().forEach(app -> applianceService.powerControl(app.getCode()));
    }

    @Scheduled(cron = "*/3 * * * * *")
    @Transactional
    public void calculateAverages() {
        List<String> referenceSensors = List.of("HOME-MB","HOME-LR","HOME-B");
        LocalDateTime averagingStartDateTime = dateUtils.getUtcLocalDateTime().minus(AVG_DURATION_MINUTES);
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndUtcTimeAfter(referenceSensors, averagingStartDateTime);
        if (indications.isEmpty()){
            return;
        }
        double averageTemp = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getTemperature()
                .getValue().doubleValue()).average().orElseThrow();
        double averageAh = indications.stream().filter(ind -> referenceSensors.contains(ind.getIndicationPlace())).mapToDouble(i -> i.getAbsoluteHumidity()
                .getValue().doubleValue()).average().orElseThrow();
        applianceRepository.updateActualByCode("AC", BigDecimal.valueOf(averageTemp).setScale(3, RoundingMode.HALF_UP).doubleValue());
        applianceRepository.updateActualByCode("DEH", BigDecimal.valueOf(averageAh).setScale(3, RoundingMode.HALF_UP).doubleValue());
        messageService.sendMessage(measurementTopic,
                ("{\"publisherId\": \"i7-4770k\", \"measurePlace\": \"HOME-AVG\", \"inOut\": \"IN\", \"air\": {\"temp\": {\"celsius\": %.3f,"
                        + " \"ah\": %.3f}}}").formatted(averageTemp, averageAh));
    }

    @Scheduled(cron = "*/3 * * * * *")
    @Transactional
    public void calculateTrends() {
        LocalDateTime utcDateTime = dateUtils.getUtcLocalDateTime();
        applianceService.calculateTrendAndSend(utcDateTime, 1);
        applianceService.calculateTrendAndSend(utcDateTime, 5);
    }

}
