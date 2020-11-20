package com.alexsoft.smarthouse.db.repository;

import com.alexsoft.smarthouse.db.entity.HouseState;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HouseStateRepository extends JpaRepository<HouseState, Integer> {

    @Query("from HouseState where messageReceived > :localDateTime")
    List<HouseState> findAfter(@Param("localDateTime") LocalDateTime localDateTime);

}
