package com.alexsoft.smarthouse.db.repository;

import com.alexsoft.smarthouse.db.entity.IndicationV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndicationRepositoryV2 extends JpaRepository<IndicationV2, Integer> {
    List<IndicationV2> findByIdGreaterThan(Integer id);
    List<IndicationV2> findByAggregationPeriod(String aggregationPeriod);
}
