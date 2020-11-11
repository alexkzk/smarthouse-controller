-- Aggregated TEMP and HUMID
select
    date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval as msg_received,
    concat(heat_indication.measure_place, '/', (CAST(count(*) as text))),
    round(CAST(float8(avg(heat_indication.temp_celsius)) as numeric),       1)  as temp,
    round(CAST(float8(avg(heat_indication.relative_humidity)) as numeric),  0)  as RH,
    round(CAST(float8(avg(heat_indication.absolute_humidity)) as numeric),  1)  as AH
from main.house_state
         inner join main.heat_indication on house_state.id = heat_indication.house_state_id
-- where measure_place = 'BALCONY'
-- where measure_place = 'TERRACE_ROOF'
-- where measure_place = 'CHERNIVTSI_AIRPORT'
-- where measure_place = 'LIVING_ROOM'
group by heat_indication.measure_place,
         date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval
order by msg_received DESC;

-- Aggregated AIR QUALITY DATA
select
        date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval as msg_received,
        concat(air_quality_indication.measure_place, '/', (CAST(count(*) as text))),
        round(CAST(float8(avg(air_quality_indication.iaq)) as numeric), 0)      as iaq$avg,
        round(CAST(float8(max(air_quality_indication.iaq)) as numeric), 0)      as iaq$max,
        round(CAST(float8(avg(air_quality_indication.pm25)) as numeric), 1)     as pm25,
        round(CAST(float8(avg(air_quality_indication.pm10)) as numeric), 1)     as pm10,
        round(CAST(float8(avg(air_quality_indication.co2)) as numeric), 0)      as co2,
        round(CAST(float8(avg(air_quality_indication.voc)) as numeric), 2)      as voc,
        round(CAST(float8(avg(co2 / 20 / (iaq+0.0000000001))) as numeric), 2)   as co2iaq,
        round(CAST(float8(avg(voc * 50 / (iaq+0.0000000001))) as numeric), 2)   as vociaq
from main.house_state
         inner join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
-- where measure_place = 'BALCONY'
-- where measure_place = 'TERRACE_ROOF'
-- where measure_place = 'CHERNIVTSI_AIRPORT'
-- where measure_place = 'LIVING_ROOM'
-- where measure_place = 'CHILDRENS' AND
--         message_received > '2020-11-10 11:10:00'
group by air_quality_indication.measure_place,
         date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval
order by msg_received DESC;

-- Aggregated WIND DATA
select
        date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval as msg_received,
        concat(wind_indication.measure_place, '/', (CAST(count(*) as text))),
        round(CAST(float8(avg(wind_indication.direction)) as numeric),         0) as direction,
        round(CAST(float8(avg(wind_indication.speed)) as numeric),    0) as speed
from main.house_state
         inner join main.wind_indication on house_state.id = wind_indication.house_state_id
-- where measure_place = 'BALCONY'
-- where measure_place = 'TERRACE_ROOF'
-- where measure_place = 'CHERNIVTSI_AIRPORT'
-- where measure_place = 'LIVING_ROOM'
group by date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min ) * :interval_min || 'minutes')::interval
order by msg_received DESC;

-- Main indications
select house_state.message_received, heat_indication.measure_place, heat_indication.temp_celsius as temp,
       heat_indication.relative_humidity as rh, heat_indication.absolute_humidity as ah,
       wind_indication.direction, wind_indication.speed,
       air_quality_indication.pm25,air_quality_indication.pm10,
       air_quality_indication.iaq,air_quality_indication.co2, air_quality_indication.voc
from main.house_state
         left join main.heat_indication on house_state.id = heat_indication.house_state_id
         left join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
         left join main.wind_indication on house_state.id = wind_indication.house_state_id
order by message_received desc LIMIT 3000;