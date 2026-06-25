package com.alexsoft.smarthouse.appliance.internal;

import com.alexsoft.smarthouse.appliance.internal.ApplianceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceGroupRepository extends JpaRepository<ApplianceGroup, Integer> {

    List<ApplianceGroup> findByTurnOffHoursIsNotNull();
    List<ApplianceGroup> findByTurnOnHoursIsNotNull();

}
