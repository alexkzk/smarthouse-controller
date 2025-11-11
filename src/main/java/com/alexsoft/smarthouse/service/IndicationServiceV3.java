package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.repository.InfluxRepository;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndicationServiceV3 {

    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final InfluxRepository influxRepository;
    private final DateUtils dateUtils;

    public IndicationV3 createIndication(IndicationV3 indication) {
        // Validate and create a new IndicationV3
        LocalDateTime utc = dateUtils.getUtc();
        if (indication.getUtcTime() == null && indication.getLocalTime() != null) {
            indication.setUtcTime(dateUtils.toUtc(indication.getLocalTime()));
        }
        if (indication.getLocalTime() == null && indication.getUtcTime() != null) {
            indication.setLocalTime(dateUtils.toLocalDateTime(indication.getUtcTime()));
        }
        if (indication.getUtcTime() == null) {
            indication.setUtcTime(utc);
        }
        if( indication.getLocalTime() == null) {
            indication.setLocalTime(dateUtils.toLocalDateTime(utc));
        }
        return save(indication);
    }

    public String getMbsStatus() {
        return "ok";
    }

    public IndicationV3 save(IndicationV3 indication) {
        influxRepository.saveAll(List.of(indication));
        return indicationRepositoryV3.save(indication);
    }

    public void saveAll(Iterable<IndicationV3> indications) {
        indicationRepositoryV3.saveAll(indications);
        influxRepository.saveAll((List) indications);
    }


}
