package com.alexsoft.smarthouse.db.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IndicationRepository extends JpaRepository<Indication, Integer> {

    @Query("from Indication as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind where hs.receivedUtc > :startDate AND hs.receivedUtc < :endDate AND hs.aggregationPeriod = 'INSTANT'")
    List<Indication> findBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("from Indication as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind where hs.receivedUtc > :startDate AND hs.receivedUtc < :endDate AND hs.aggregationPeriod = :aggregationPeriod")
    List<Indication> findBetween(LocalDateTime startDate, LocalDateTime endDate, AggregationPeriod aggregationPeriod);

    @Override
    @Query("from Indication as hs left join fetch hs.air as air left join fetch" +
            " air.pressure left join fetch air.quality left join fetch air.temp" +
            " left join fetch air.wind")
    List<Indication> findAll();

    @Query(value = "select received_local as msg_received,\n"
            + "       in_out,\n"
            + "       indication_place,\n"
            + "       aggregation_period as period,\n"
            + "       round(CAST(float8(celsius) as numeric), 1)                  as temp,\n"
            + "       round(CAST(float8(rh) as numeric), 0)                       as rh,\n"
            + "       round(CAST(float8(ah) as numeric), 1)                       as ah,\n"
            + "       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,\n"
            + "       round(CAST(float8(direction) as numeric), 0) as direction,\n"
            + "       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms\n"
            + "from main.indication\n"
            + "         left join main.air a on a.id = indication.air_id\n"
            + "         left join main.air_temp_indication t on t.id = a.temp_id\n"
            + "         left join main.air_pressure_indication ap on ap.id = a.pressure_id\n"
            + "         left join main.air_wind_indication w on w.id = a.wind_id\n"
            + "where  date_trunc('day', received_local) = date_trunc('day', now() at time zone 'utc')\n"
            + "  AND date_trunc('month', received_local) = date_trunc('month', now() at time zone 'utc')\n"
            + "  AND date_trunc('year', received_local) = date_trunc('year', now() at time zone 'utc')\n"
            + "  AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', received_local) <= 1\n"
            + "  AND aggregation_period = 'MINUTELY'\n"
            + "union all\n"
            + "select received_local as msg_received,\n"
            + "       in_out,\n"
            + "       indication_place,\n"
            + "       aggregation_period as period,\n"
            + "       round(CAST(float8(celsius) as numeric), 1)                  as temp,\n"
            + "       round(CAST(float8(rh) as numeric), 0)                       as rh,\n"
            + "       round(CAST(float8(ah) as numeric), 1)                       as ah,\n"
            + "       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,\n"
            + "       round(CAST(float8(direction) as numeric), 0) as direction,\n"
            + "       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms\n"
            + "from main.indication\n"
            + "         left join main.air a on a.id = indication.air_id\n"
            + "         left join main.air_temp_indication t on t.id = a.temp_id\n"
            + "         left join main.air_pressure_indication ap on ap.id = a.pressure_id\n"
            + "         left join main.air_wind_indication w on w.id = a.wind_id\n"
            + "where DATE_PART('day', AGE(now() at time zone 'utc', received_local)) <= 5\n"
            + "  AND DATE_PART('month', AGE(now() at time zone 'utc', received_local)) = 0\n"
            + "  AND DATE_PART('year', AGE(now() at time zone 'utc', received_local)) = 0\n"
            + "  AND aggregation_period = 'HOURLY' order by msg_received desc", nativeQuery = true)
    List<Map<String, Object>> getAggregatedHourlyAndMinutely();

    @Query(value = "select received_local as msg_received,\n"
            + "       in_out,\n"
            + "       indication_place,\n"
            + "       aggregation_period as period,\n"
            + "       round(CAST(float8(celsius) as numeric), 1)                  as temp,\n"
            + "       round(CAST(float8(rh) as numeric), 0)                       as rh,\n"
            + "       round(CAST(float8(ah) as numeric), 1)                       as ah,\n"
            + "       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,\n"
            + "       round(CAST(float8(direction) as numeric), 0) as direction,\n"
            + "       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms\n"
            + "from main.indication\n"
            + "         left join main.air a on a.id = indication.air_id\n"
            + "         left join main.air_temp_indication t on t.id = a.temp_id\n"
            + "         left join main.air_pressure_indication ap on ap.id = a.pressure_id\n"
            + "         left join main.air_wind_indication w on w.id = a.wind_id\n"
            + "where "
            + "     indication_place LIKE :place AND "
            + "     aggregation_period LIKE :period AND "
            + "     DATE_PART('day', AGE(now() at time zone 'utc', received_local)) <= 30\n  AND "
            + "     DATE_PART('month', AGE(now() at time zone 'utc', received_local)) = 0\n  AND "
            + "     DATE_PART('year', AGE(now() at time zone 'utc', received_local)) = 0\n  "
            + "order by msg_received desc"
            , nativeQuery = true)
    List<Map<String, Object>> getAggregatedDaily(String place, String period);

    @Query(value = "select received_local as msg_received,\n"
            + "       in_out,\n"
            + "       indication_place,\n"
            + "       aggregation_period as period,\n"
            + "       round(CAST(float8(celsius) as numeric), 1)                  as temp,\n"
            + "       round(CAST(float8(rh) as numeric), 0)                       as rh,\n"
            + "       round(CAST(float8(ah) as numeric), 1)                       as ah,\n"
            + "       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,\n"
            + "       round(CAST(float8(direction) as numeric), 0) as direction,\n"
            + "       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms\n"
            + "from main.indication\n"
            + "         left join main.air a on a.id = indication.air_id\n"
            + "         left join main.air_temp_indication t on t.id = a.temp_id\n"
            + "         left join main.air_pressure_indication ap on ap.id = a.pressure_id\n"
            + "         left join main.air_wind_indication w on w.id = a.wind_id\n"
            + "where "
            + "     indication_place LIKE :place AND "
            + "     aggregation_period LIKE :period "
            + "order by msg_received desc"
            , nativeQuery = true)
    List<Map<String, Object>> getAggregatedMonthly(String place, String period);
}
