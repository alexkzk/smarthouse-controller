package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.IndicationV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IndicationRepositoryV2 extends JpaRepository<IndicationV2, Integer> {
    List<IndicationV2> findByIdGreaterThan(Integer id);
    List<IndicationV2> findByAggregationPeriod(String aggregationPeriod);
    List<IndicationV2> findByIndicationPlaceInAndUtcTimeAfter(List<String> indicationPlace, LocalDateTime utc);
}
