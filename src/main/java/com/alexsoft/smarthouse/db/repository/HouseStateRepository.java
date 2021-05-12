package com.alexsoft.smarthouse.db.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.alexsoft.smarthouse.db.entity.HouseState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseStateRepository extends JpaRepository<HouseState, Integer> {

    @Query("from HouseState as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind where hs.messageReceived > :localDateTime")
    List<HouseState> findAfter(@Param("localDateTime") LocalDateTime localDateTime);

    @Query("from HouseState as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind")
    List<HouseState> findAll();

    @Query(value = "(select date_trunc('hour', message_received) +\n" +
            "        cast((FLOOR(DATE_PART('minute', message_received) / 5) * 5 || 'minutes') as interval) as msg_received,\n" +
            "        in_out,\n" +
            "        measure_place,\n" +
            "        'MINUTELY'                                        as period,\n" +
            "        round(CAST(float8(avg(celsius)) as numeric), 1)   as temp,\n" +
            "        round(CAST(float8(avg(rh)) as numeric), 0)        as rh,\n" +
            "        round(CAST(float8(avg(ah)) as numeric), 1)        as ah,\n" +
            "        round(CAST(float8(avg(mm_hg)) as numeric), 0)     as mm_hg,\n" +
            "        round(CAST(float8(avg(pm10)) as numeric), 1)      as pm10,\n" +
            "        round(CAST(float8(avg(pm25)) as numeric), 1)      as pm25,\n" +
            "        round(CAST(float8(avg(iaq)) as numeric), 0)       as iaq,\n" +
            "        round(CAST(float8(max(iaq)) as numeric), 0)       as iaq_max,\n" +
            "        round(CAST(float8(avg(bme680static_iaq)) as numeric), 0)       as siaq,\n" +
            "        round(CAST(float8(max(bme680static_iaq)) as numeric), 0)       as siaq_max,\n" +
            "        round(CAST(float8(avg(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance,\n" +
            "        round(CAST(float8(min(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance_min,\n" +
            "        round(CAST(float8(max(direction)) as numeric), 0) as direction,\n" +
            "        round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms\n" +
            " from main.house_state_v2\n" +
            "          left join main.air a on a.id = house_state_v2.air_id\n" +
            "          left join main.air_temp t on t.id = a.temp_id\n" +
            "          left join main.air_pressure ap on ap.id = a.pressure_id\n" +
            "          left join main.air_quality aq on a.quality_id = aq.id\n" +
            "          left join main.bme680meta b680m on b680m.id = aq.bme680meta_id\n" +
            "          left join main.wind w on w.id = a.wind_id\n" +
            " where date_trunc('day', message_received) = date_trunc('day', now() at time zone 'utc')\n" +
            "   AND date_trunc('month', message_received) = date_trunc('month', now() at time zone 'utc')\n" +
            "   AND date_trunc('year', message_received) = date_trunc('year', now() at time zone 'utc')\n" +
            "   AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', message_received) <= 1\n" +
            " group by msg_received, in_out, measure_place\n" +
            " union all\n" +
            " select date_trunc('hour', message_received) +\n" +
            "        cast((FLOOR(DATE_PART('minute', message_received) / 60) * 60 || 'minutes') as interval) as msg_received,\n" +
            "        in_out,\n" +
            "        measure_place,\n" +
            "        'HOURLY'                                                         as period,\n" +
            "        round(CAST(float8(avg(celsius)) as numeric), 1)                  as temp,\n" +
            "        round(CAST(float8(avg(rh)) as numeric), 0)                       as rh,\n" +
            "        round(CAST(float8(avg(ah)) as numeric), 1)                       as ah,\n" +
            "        round(CAST(float8(avg(mm_hg)) as numeric), 0)                    as mm_hg,\n" +
            "        round(CAST(float8(avg(pm10)) as numeric), 1)                     as pm10,\n" +
            "        round(CAST(float8(avg(pm25)) as numeric), 1)                     as pm25,\n" +
            "        round(CAST(float8(avg(iaq)) as numeric), 0)                      as iaq,\n" +
            "        round(CAST(float8(max(iaq)) as numeric), 0)                      as iaq_max,\n" +
            "        round(CAST(float8(avg(bme680static_iaq)) as numeric), 0)         as siaq,\n" +
            "        round(CAST(float8(max(bme680static_iaq)) as numeric), 0)         as siaq_max,\n" +
            "        round(CAST(float8(avg(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance,\n" +
            "        round(CAST(float8(min(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance_min,\n" +
            "        round(CAST(float8(max(direction)) as numeric), 0) as direction,\n" +
            "        round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms\n" +
            " from main.house_state_v2\n" +
            "          left join main.air a on a.id = house_state_v2.air_id\n" +
            "          left join main.air_temp t on t.id = a.temp_id\n" +
            "          left join main.air_pressure ap on ap.id = a.pressure_id\n" +
            "          left join main.air_quality aq on a.quality_id = aq.id\n" +
            "          left join main.bme680meta b680m on b680m.id = aq.bme680meta_id\n" +
            "          left join main.wind w on w.id = a.wind_id\n" +
            " where message_received <\n" +
            "       (select min(msg_received)\n" +
            "        from (select date_trunc('hour', message_received) +\n" +
            "                     cast((FLOOR(DATE_PART('minute', message_received) / 5) * 5 || 'minutes') as interval) as msg_received\n" +
            "              from main.house_state_v2\n" +
            "                       left join main.air a on a.id = house_state_v2.air_id\n" +
            "                       left join main.air_temp t on t.id = a.temp_id\n" +
            "                       left join main.air_pressure ap on ap.id = a.pressure_id\n" +
            "                       left join main.air_quality aq on a.quality_id = aq.id\n" +
            "                       left join main.bme680meta b680m on b680m.id = aq.bme680meta_id\n" +
            "                       left join main.wind w on w.id = a.wind_id\n" +
            "              where date_trunc('day', message_received) = date_trunc('day', now() at time zone 'utc')\n" +
            "                AND date_trunc('month', message_received) = date_trunc('month', now() at time zone 'utc')\n" +
            "                AND date_trunc('year', message_received) = date_trunc('year', now() at time zone 'utc')\n" +
            "                AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', message_received) <= 1\n" +
            "              group by msg_received, in_out, measure_place\n" +
            "              order by msg_received DESC) as mriomp)\n" +
            "   AND DATE_PART('day', AGE(now() at time zone 'utc', message_received)) <= 2\n" +
            "   AND DATE_PART('month', AGE(now() at time zone 'utc', message_received)) = 0\n" +
            "   AND DATE_PART('year', AGE(now() at time zone 'utc', message_received)) = 0\n" +
            " group by msg_received, in_out, measure_place\n" +
            ") order by msg_received DESC, measure_place", nativeQuery = true)
    List<Map<String, Object>> aggregate();


}
