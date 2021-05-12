(select date_trunc('hour', message_received) +
        cast((FLOOR(DATE_PART('minute', message_received) / 5) * 5 || 'minutes') as interval) as msg_received,
        in_out,
        measure_place,
        'MINUTELY'                                        as period,
        round(CAST(float8(avg(celsius)) as numeric), 1)   as temp,
        round(CAST(float8(avg(rh)) as numeric), 0)        as rh,
        round(CAST(float8(avg(ah)) as numeric), 1)        as ah,
        round(CAST(float8(avg(mm_hg)) as numeric), 0)     as mm_hg,
        round(CAST(float8(avg(pm10)) as numeric), 1)      as pm10,
        round(CAST(float8(avg(pm25)) as numeric), 1)      as pm25,
        round(CAST(float8(avg(iaq)) as numeric), 0)       as iaq,
        round(CAST(float8(max(iaq)) as numeric), 0)       as iaq_max,
        round(CAST(float8(avg(bme680static_iaq)) as numeric), 0)       as siaq,
        round(CAST(float8(max(bme680static_iaq)) as numeric), 0)       as siaq_max,
        round(CAST(float8(avg(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance,
        round(CAST(float8(min(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance_min,
        round(CAST(float8(max(direction)) as numeric), 0) as direction,
        round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms
 from main.house_state_v2
          left join main.air a on a.id = house_state_v2.air_id
          left join main.air_temp t on t.id = a.temp_id
          left join main.air_pressure ap on ap.id = a.pressure_id
          left join main.air_quality aq on a.quality_id = aq.id
          left join main.bme680meta b680m on b680m.id = aq.bme680meta_id
          left join main.wind w on w.id = a.wind_id
 where date_trunc('day', message_received) = date_trunc('day', now() at time zone 'utc')
   AND date_trunc('month', message_received) = date_trunc('month', now() at time zone 'utc')
   AND date_trunc('year', message_received) = date_trunc('year', now() at time zone 'utc')
   AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', message_received) <= 1
 group by msg_received, in_out, measure_place
 union all
 select date_trunc('hour', message_received) +
        cast((FLOOR(DATE_PART('minute', message_received) / 60) * 60 || 'minutes') as interval) as msg_received,
        in_out,
        measure_place,
        'HOURLY'                                                         as period,
        round(CAST(float8(avg(celsius)) as numeric), 1)                  as temp,
        round(CAST(float8(avg(rh)) as numeric), 0)                       as rh,
        round(CAST(float8(avg(ah)) as numeric), 1)                       as ah,
        round(CAST(float8(avg(mm_hg)) as numeric), 0)                    as mm_hg,
        round(CAST(float8(avg(pm10)) as numeric), 1)                     as pm10,
        round(CAST(float8(avg(pm25)) as numeric), 1)                     as pm25,
        round(CAST(float8(avg(iaq)) as numeric), 0)                      as iaq,
        round(CAST(float8(max(iaq)) as numeric), 0)                      as iaq_max,
        round(CAST(float8(avg(bme680static_iaq)) as numeric), 0)         as siaq,
        round(CAST(float8(max(bme680static_iaq)) as numeric), 0)         as siaq_max,
        round(CAST(float8(avg(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance,
        round(CAST(float8(min(bme680gas_resistance)) as numeric)/2000, 0)       as gas_resistance_min,
        round(CAST(float8(max(direction)) as numeric), 0) as direction,
        round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms
 from main.house_state_v2
          left join main.air a on a.id = house_state_v2.air_id
          left join main.air_temp t on t.id = a.temp_id
          left join main.air_pressure ap on ap.id = a.pressure_id
          left join main.air_quality aq on a.quality_id = aq.id
          left join main.bme680meta b680m on b680m.id = aq.bme680meta_id
          left join main.wind w on w.id = a.wind_id
 where message_received <
       (select min(msg_received)
        from (select date_trunc('hour', message_received) +
                     cast((FLOOR(DATE_PART('minute', message_received) / 5) * 5 || 'minutes') as interval) as msg_received
              from main.house_state_v2
                       left join main.air a on a.id = house_state_v2.air_id
                       left join main.air_temp t on t.id = a.temp_id
                       left join main.air_pressure ap on ap.id = a.pressure_id
                       left join main.air_quality aq on a.quality_id = aq.id
                       left join main.bme680meta b680m on b680m.id = aq.bme680meta_id
                       left join main.wind w on w.id = a.wind_id
              where date_trunc('day', message_received) = date_trunc('day', now() at time zone 'utc')
                AND date_trunc('month', message_received) = date_trunc('month', now() at time zone 'utc')
                AND date_trunc('year', message_received) = date_trunc('year', now() at time zone 'utc')
                AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', message_received) <= 1
              group by msg_received, in_out, measure_place
              order by msg_received DESC) as mriomp)
   AND DATE_PART('day', AGE(now() at time zone 'utc', message_received)) <= 2
   AND DATE_PART('month', AGE(now() at time zone 'utc', message_received)) = 0
   AND DATE_PART('year', AGE(now() at time zone 'utc', message_received)) = 0
 group by msg_received, in_out, measure_place
) order by msg_received DESC, measure_place;