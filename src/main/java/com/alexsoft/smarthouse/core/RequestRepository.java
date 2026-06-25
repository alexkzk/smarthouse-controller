package com.alexsoft.smarthouse.core;

import com.alexsoft.smarthouse.core.Request;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Integer> {

}
