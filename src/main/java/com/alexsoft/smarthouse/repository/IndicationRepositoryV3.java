package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.IndicationV3;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IndicationRepositoryV3 extends JpaRepository<IndicationV3, Integer> {
    List<IndicationV3> findByDeviceIdInAndUtcTimeIsAfterAndMeasurementType(List<String> deviceIds, LocalDateTime localTime, String measurementType);
}
