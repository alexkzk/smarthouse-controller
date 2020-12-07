package com.alexsoft.smarthouse.db.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseStateV2Repository extends JpaRepository<HouseStateV2, Integer> {

    @Query("from HouseStateV2 where messageReceived > :localDateTime")
    List<HouseStateV2> findAfter(@Param("localDateTime") LocalDateTime localDateTime);

    /*@Query("from HouseStateV2 left join fetch Quality left join fetch AirQualityMeta "
        + "left join fetch Temp left join fetch Pressure left join fetch Wind ")
    List<HouseStateV2> findAll();*/


}
