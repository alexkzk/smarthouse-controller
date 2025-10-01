package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledService {

    public static final Duration AVG_DURATION_MINUTES = Duration.ofMinutes(1);
    private final ApplianceService applianceService;
    private final DateUtils dateUtils;
    private final MessageService messageService;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final ApplianceRepository applianceRepository;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Scheduled(cron = "0/10 * * * * ?")
    @Transactional
    public void powerControl() {
        applianceService.getAllAppliances().forEach(app -> applianceService.powerControl(app.getCode()));
    }

    @Scheduled(cron = "*/3 * * * * *")
    @Transactional
    public void calculateTrends() {
        LocalDateTime utcDateTime = dateUtils.getUtcLocalDateTime();
        calculateTrendAndSend(utcDateTime, 1);
        calculateTrendAndSend(utcDateTime, 5);
    }


    public void calculateTrendAndSend(LocalDateTime localDateTime, int minutes) {
        Comparator<IndicationV2> comparator = Comparator.comparing(IndicationV2::getLocalTime);
        Double temperatureTrend;
        Double ahTrend;

        List<IndicationV2> averages = indicationRepositoryV2.findByIndicationPlaceInAndUtcTimeAfter(
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
