package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Integer> {

}
