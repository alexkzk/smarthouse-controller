package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SummaryController {

    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final DateUtils dateUtils;

    @GetMapping( "/status-bar")
    public String getStatusBarString() {

        LocalDateTime utcMinusFiveMinutes = dateUtils.getUtc().minusMinutes(5);

        OptionalDouble avgTemp = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List.of("HOME-AVG"),
                utcMinusFiveMinutes, "temp").stream().mapToDouble(IndicationV3::getValue).average();
        OptionalDouble avgAh = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List.of("HOME-AVG"),
                utcMinusFiveMinutes, "ah").stream().mapToDouble(IndicationV3::getValue).average();
        OptionalDouble avgBtc = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List.of("BTC"),
                utcMinusFiveMinutes, "money").stream().mapToDouble(IndicationV3::getValue).average();

        String btcFormatted = avgBtc.isPresent()
                ? BigDecimal.valueOf(avgBtc.getAsDouble() / 1000)
                .setScale(1, RoundingMode.HALF_UP)
                .toString()
                : "???";

        String tempFormatted = avgTemp.isPresent()
                ? BigDecimal.valueOf(avgTemp.getAsDouble())
                .setScale(1, RoundingMode.HALF_UP)
                .toString()
                : "?";

        String ahFormatted = avgAh.isPresent()
                ? BigDecimal.valueOf(avgAh.getAsDouble())
                .setScale(1, RoundingMode.HALF_UP)
                .toString()
                : "?";

        return String.format("%s %s/%s", btcFormatted, tempFormatted, ahFormatted);
    }

}
