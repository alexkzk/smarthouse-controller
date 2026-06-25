package com.alexsoft.smarthouse.appliance.internal;

import com.alexsoft.smarthouse.appliance.internal.ApartmentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApartmentDetailsRepository extends JpaRepository<ApartmentDetails, Long> {
}
