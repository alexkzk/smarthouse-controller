-- AGGREGATE MINUTELY/HOURLY CALCULATING AVERAGE BY DATABASE
(select date_trunc('hour', received) +
        cast((FLOOR(DATE_PART('minute', received) / 5) * 5 || 'minutes') as interval) as msg_received,
        in_out,
        indication_place,
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
 from main.indication
          left join main.air a on a.id = indication.air_id
          left join main.air_temp_indication t on t.id = a.temp_id
          left join main.air_pressure_indication ap on ap.id = a.pressure_id
          left join main.air_quality_indication aq on a.quality_id = aq.id
          left join main.bme_680_meta b680m on b680m.id = aq.bme680meta_id
          left join main.air_wind_indication w on w.id = a.wind_id
 where date_trunc('day', received) = date_trunc('day', now() at time zone 'utc')
   AND date_trunc('month', received) = date_trunc('month', now() at time zone 'utc')
   AND date_trunc('year', received) = date_trunc('year', now() at time zone 'utc')
   AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', received) <= 1
 group by msg_received, in_out, indication_place

 union all

 select date_trunc('hour', received) +
        cast((FLOOR(DATE_PART('minute', received) / 60) * 60 || 'minutes') as interval) as msg_received,
        in_out,
        indication_place,
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
 from main.indication
          left join main.air a on a.id = indication.air_id
          left join main.air_temp_indication t on t.id = a.temp_id
          left join main.air_pressure_indication ap on ap.id = a.pressure_id
          left join main.air_quality_indication aq on a.quality_id = aq.id
          left join main.bme_680_meta b680m on b680m.id = aq.bme680meta_id
          left join main.air_wind_indication w on w.id = a.wind_id
 where received <
       (select min(msg_received)
        from (select date_trunc('hour', received) +
                     cast((FLOOR(DATE_PART('minute', received) / 5) * 5 || 'minutes') as interval) as msg_received
              from main.indication
                       left join main.air a on a.id = indication.air_id
                       left join main.air_temp_indication t on t.id = a.temp_id
                       left join main.air_pressure_indication ap on ap.id = a.pressure_id
                       left join main.air_quality_indication aq on a.quality_id = aq.id
                       left join main.bme_680_meta b680m on b680m.id = aq.bme680meta_id
                       left join main.air_wind_indication w on w.id = a.wind_id
              where date_trunc('day', received) = date_trunc('day', now() at time zone 'utc')
                AND date_trunc('month', received) = date_trunc('month', now() at time zone 'utc')
                AND date_trunc('year', received) = date_trunc('year', now() at time zone 'utc')
                AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', received) <= 1
              group by msg_received, in_out, indication_place
              order by msg_received DESC) as mriomp)
   AND DATE_PART('day', AGE(now() at time zone 'utc', received)) <= 2
   AND DATE_PART('month', AGE(now() at time zone 'utc', received)) = 0
   AND DATE_PART('year', AGE(now() at time zone 'utc', received)) = 0
 group by msg_received, in_out, indication_place
) order by msg_received DESC, indication_place;


-- AGGREGATE MINUTELY/HOURLY CALCULATING SELECTING AVERAGE FROM DATABASE
select received_utc as msg_received,
       in_out,
       indication_place,
       aggregation_period as period,
       round(CAST(float8(celsius) as numeric), 1)                  as temp,
       round(CAST(float8(rh) as numeric), 0)                       as rh,
       round(CAST(float8(ah) as numeric), 1)                       as ah,
       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,
       round(CAST(float8(direction) as numeric), 0) as direction,
       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms
from main.indication
         left join main.air a on a.id = indication.air_id
         left join main.air_temp_indication t on t.id = a.temp_id
         left join main.air_pressure_indication ap on ap.id = a.pressure_id
         left join main.air_wind_indication w on w.id = a.wind_id
where  date_trunc('day', received_utc) = date_trunc('day', now() at time zone 'utc')
  AND date_trunc('month', received_utc) = date_trunc('month', now() at time zone 'utc')
  AND date_trunc('year', received_utc) = date_trunc('year', now() at time zone 'utc')
  AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', received_utc) <= 1
  AND aggregation_period = 'MINUTELY'
union all
select received_utc as msg_received,
       in_out,
       indication_place,
       aggregation_period as period,
       round(CAST(float8(celsius) as numeric), 1)                  as temp,
       round(CAST(float8(rh) as numeric), 0)                       as rh,
       round(CAST(float8(ah) as numeric), 1)                       as ah,
       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,
       round(CAST(float8(direction) as numeric), 0) as direction,
       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms
from main.indication
         left join main.air a on a.id = indication.air_id
         left join main.air_temp_indication t on t.id = a.temp_id
         left join main.air_pressure_indication ap on ap.id = a.pressure_id
         left join main.air_wind_indication w on w.id = a.wind_id
where
  DATE_PART('day', AGE(now() at time zone 'utc', received_utc)) <= 5
  AND DATE_PART('month', AGE(now() at time zone 'utc', received_utc)) = 0
  AND DATE_PART('year', AGE(now() at time zone 'utc', received_utc)) = 0
  AND aggregation_period = 'HOURLY' order by msg_received desc;


select date_trunc('hour', received) +
       cast((FLOOR(DATE_PART('minute', received) / 60) * 60 || 'minutes') as interval) as msg_received,
       in_out,
       indication_place,
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
from main.indication
         left join main.air a on a.id = indication.air_id
         left join main.air_temp_indication t on t.id = a.temp_id
         left join main.air_pressure_indication ap on ap.id = a.pressure_id
         left join main.air_quality_indication aq on a.quality_id = aq.id
         left join main.bme_680_meta b680m on b680m.id = aq.bme680meta_id
         left join main.air_wind_indication w on w.id = a.wind_id
group by msg_received, in_out, indication_place order by msg_received DESC;


select ati.celsius from main.indication ind
                            inner join main.air a on ind.air_id = a.id
                            inner join main.air_temp_indication ati on ati.id = a.temp_id
where indication_place = 'NORTH' and (received_utc > '2021-07-03 00:00:00.000000' and received_utc < '2021-07-03 23:59:00.000000');

update main.air_temp_indication set celsius = null, ah = null, rh = null where id in(
    select temp_id from main.air_temp_indication temp inner join main.air air on air.temp_id = temp.id
                                                      inner join main.indication ind on air.id = ind.air_id
    where indication_place = 'TERRACE' AND in_out = 'OUT' and received > '2021-07-03 23:59:00.000000'
      AND (celsius < 20 OR celsius > 40 OR ah > 20 OR ah < 8 OR celsius is null)
    );

update main.air_quality_indication set pm10 = null, pm25 = null where id in(
    select quality_id
    from main.air_quality_indication temp
             inner join main.air air on air.quality_id = temp.id
             inner join main.indication ind on air.id = ind.air_id
    where (temp.pm25 > 25 OR temp.pm10 > 25)
      AND received > '2021-06-01 00:00:00.000000');

select  received, quality_id, pm10, pm25
from main.air_quality_indication temp
         inner join main.air air on air.quality_id = temp.id
         inner join main.indication ind on air.id = ind.air_id
-- where (temp.pm25 > 40 OR temp.pm10 > 40)
  AND received > '2021-06-05 00:00:00.000000' order by received;

select *
from main.air_quality_indication where id in (select quality_id
                                              from main.air_quality_indication temp
                                                       inner join main.air air on air.quality_id = temp.id
                                                       inner join main.indication ind on air.id = ind.air_id
                                              where (temp.pm25 > 25 OR temp.pm10 > 25)
                                                AND received > '2021-06-01 00:00:00.000000') ORDER BY id;

select temp_id from main.air_temp_indication temp inner join main.air air on air.temp_id = temp.id
                                                  inner join main.indication ind on air.id = ind.air_id
where (celsius < -50 OR celsius > 50)

select ind.received_utc, ati.celsius, ati.rh, ati.ah  from main.indication ind
                            inner join main.air a on ind.air_id = a.id
                            inner join main.air_temp_indication ati on ati.id = a.temp_id
where indication_place = '1201S-OCEAN-DR' and aggregation_period = 'DAILY';