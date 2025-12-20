package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.alexsoft.smarthouse.utils.DateUtils.getLocalDateTime;
import static com.alexsoft.smarthouse.utils.DateUtils.getUtc;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledService {

    public static final List<String> TREND_DEVICE_IDS = List.of("HOME-MB", "HOME-LR");
    public static final List<String> TREND_MEASURE_TYPES = List.of("ah", "temp");
    private final ApplianceService applianceService;
    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final IndicationServiceV3 indicationServiceV3;

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
        LocalDateTime utcDateTime = getUtc();
        TREND_MEASURE_TYPES.forEach(measurementType -> {
            calculateTrendAndSend(utcDateTime, 1, measurementType, TREND_DEVICE_IDS);
            calculateTrendAndSend(utcDateTime, 5, measurementType, TREND_DEVICE_IDS);
        });
    }


    public void calculateTrendAndSend(LocalDateTime utcDateTime, int minutes, String measurementType, List<String> deviceIds) {
        Comparator<IndicationV3> comparator = Comparator.comparing(IndicationV3::getUtcTime);
        List<IndicationV3> averages = indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(
                deviceIds, utcDateTime.minusMinutes(minutes), measurementType);
        Optional<IndicationV3> first = averages.stream().max(comparator);
        Optional<IndicationV3> second = averages.stream().min(comparator);
        if (first.map(IndicationV3::getValue).isEmpty() || second.map(IndicationV3::getValue).isEmpty()) {
            return;
        }
        long secondsDifference = Duration.between(second.get().getLocalTime(), first.get().getLocalTime()).toSeconds();
        if (secondsDifference < ((long) minutes * 60 * 0.85)) {
            return;
        }
        Double trend = (first.get().getValue() - second.get().getValue()) / secondsDifference * 3600;
        indicationServiceV3.save(IndicationV3.builder().locationId("HOME-TREND%d".formatted(minutes)).localTime(getLocalDateTime()).utcTime(getUtc())
                .publisherId("smarthouse-server").measurementType(measurementType.equals("ah") ? "ah" : "temp").value(trend).build());
    }

}
