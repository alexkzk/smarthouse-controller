package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceRepository extends JpaRepository<Appliance, String> {

}
