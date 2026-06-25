package com.alexsoft.smarthouse.environment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IndicationRepositoryV3 extends JpaRepository<IndicationV3, Integer> {
    List<IndicationV3> findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(List<String> locationIds, LocalDateTime utc, String measurementType);
    Optional<IndicationV3> findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc(String locationId, String measurementType);
    List<IndicationV3> findByUtcTimeBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<IndicationV3> findByLocalTimeBetweenAndMeasurementType(LocalDateTime startDate, LocalDateTime endDate, String measurementType);

    @Query("select avg(i.value) from IndicationV3 i where i.locationId in :locationIds and i.utcTime > :after and i.measurementType = :measurementType")
    Optional<Double> findAvgValueByLocationIdInAndUtcTimeAfterAndMeasurementType(
            @Param("locationIds") List<String> locationIds,
            @Param("after") LocalDateTime after,
            @Param("measurementType") String measurementType);

    @Query("select min(i.utcTime) from IndicationV3 i")
    LocalDateTime findMinUtcTime();

    @Query("select max(i.utcTime) from IndicationV3 i")
    LocalDateTime findMaxUtcTime();
}
