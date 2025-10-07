package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.ApplianceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceGroupRepository extends JpaRepository<ApplianceGroup, Integer> {

    List<ApplianceGroup> findByTurnOffHoursIsNotNull();
    List<ApplianceGroup> findByTurnOnHoursIsNotNull();

}
