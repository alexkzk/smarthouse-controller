package com.alexsoft.smarthouse.db.repository;

import com.alexsoft.smarthouse.db.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceRepository extends JpaRepository<Appliance, String> {

}
