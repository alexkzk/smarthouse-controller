package com.alexsoft.smarthouse.db.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseStateV2Repository extends JpaRepository<HouseStateV2, Integer> {

    @Query("from HouseStateV2 as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind where hs.messageReceived > :localDateTime")
    List<HouseStateV2> findAfter(@Param("localDateTime") LocalDateTime localDateTime);

    @Query("from HouseStateV2 as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind")
    List<HouseStateV2> findAll();


}
