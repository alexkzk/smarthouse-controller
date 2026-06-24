package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.ApartmentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApartmentDetailsRepository extends JpaRepository<ApartmentDetails, Long> {
}
