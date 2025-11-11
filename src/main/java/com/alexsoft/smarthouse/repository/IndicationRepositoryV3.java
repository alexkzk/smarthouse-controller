package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.IndicationV3;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IndicationRepositoryV3 extends JpaRepository<IndicationV3, Integer> {
    List<IndicationV3> findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(List<String> locationIds, LocalDateTime utc, String measurementType);
    List<IndicationV3> findByUtcTimeBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<IndicationV3> findByLocalTimeBetweenAndMeasurementType(LocalDateTime startDate, LocalDateTime endDate, String measurementType);
}
