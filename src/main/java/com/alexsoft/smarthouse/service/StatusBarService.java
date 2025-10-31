package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Service
@RequiredArgsConstructor
public class StatusBarService {

    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final DateUtils dateUtils;
    private final ApplianceRepository applianceRepository;

    @Transactional
    public String getStatusBarString() {

        LocalDateTime utcMinusFiveMinutes = dateUtils.getUtc().minusMinutes(5);
        Optional<Appliance> ac = applianceRepository.findById("AC");
        Optional<Appliance> deh = applianceRepository.findById("DEH");
        OptionalDouble avgBtc = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List.of("BTC"),
                utcMinusFiveMinutes, "money").stream().mapToDouble(IndicationV3::getValue).average();
        OptionalDouble avgOutT = indicationRepositoryV3.findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List.of("out"),
                utcMinusFiveMinutes, "temp").stream().mapToDouble(IndicationV3::getValue).average();

        String btcFormatted = avgBtc.isPresent()
                ? BigDecimal.valueOf(avgBtc.getAsDouble() / 1000)
                .setScale(1, RoundingMode.HALF_UP)
                .toString()
                : "???.?";

        String tempFormatted = ac.map(Appliance::getActual)
                .map(actual -> BigDecimal.valueOf(actual).setScale(2, RoundingMode.HALF_UP).toString())
                .orElse("??.??");

        String ahFormatted = deh.map(Appliance::getActual)
                .map(actual -> BigDecimal.valueOf(actual).setScale(2, RoundingMode.HALF_UP).toString())
                .orElse("??.??");

        String outTempFormatted = avgOutT.isPresent() ? BigDecimal.valueOf(avgOutT.getAsDouble()).setScale(1, RoundingMode.HALF_UP).toString() : "??.?";

        return String.format("%s    %s/%s    %s", btcFormatted, tempFormatted, ahFormatted, outTempFormatted);
    }

}
