package com.alexsoft.smarthouse.appliance;

import com.alexsoft.smarthouse.appliance.ApartmentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApartmentDetailsRepository extends JpaRepository<ApartmentDetails, Long> {
}
