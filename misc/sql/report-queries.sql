select message_received                                                     as date,
       round(CAST(float8(heat_indication.temp_celsius) as numeric), 1)      as temp,
       round(CAST(float8(heat_indication.relative_humidity) as numeric), 0) as humid,
       round(CAST(float8(air_quality_indication.co2) as numeric), 0)        as CO2,
       round(CAST(float8(air_quality_indication.iaq) as numeric), 0)        as IAQ,
       round(CAST(float8(air_quality_indication.voc) as numeric), 2)        as VOQ
from main.house_state
         join main.heat_indication on house_state.id = heat_indication.house_state_id
         join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
where heat_indication.measure_place = 'CHILDRENS'
order by date DESC;


select date_trunc('minute', message_received)                                    as date,
       round(CAST(float8(avg(heat_indication.temp_celsius)) as numeric), 1)      as temp,
       round(CAST(float8(avg(heat_indication.relative_humidity)) as numeric), 0) as humid,
       round(CAST(float8(avg(air_quality_indication.co2)) as numeric), 0)        as CO2,
       round(CAST(float8(avg(air_quality_indication.iaq)) as numeric), 0)        as IAQ,
       round(CAST(float8(avg(air_quality_indication.voc)) as numeric), 2)        as VOQ
from main.house_state
         join main.heat_indication on house_state.id = heat_indication.house_state_id
         join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
where heat_indication.measure_place = 'CHILDRENS'
group by date_trunc('minute', message_received)
order by date DESC;


select date_trunc('hour', message_received)                                      as date,
       FLOOR(DATE_PART('minute', message_received) / 10)                         as minute,
       round(CAST(float8(avg(heat_indication.temp_celsius)) as numeric), 1)      as temp,
       round(CAST(float8(avg(heat_indication.relative_humidity)) as numeric), 0) as humid,
       round(CAST(float8(avg(air_quality_indication.co2)) as numeric), 0)        as CO2,
       round(CAST(float8(avg(air_quality_indication.iaq)) as numeric), 0)        as IAQ,
       round(CAST(float8(avg(air_quality_indication.voc)) as numeric), 2)        as VOQ
from main.house_state
         join main.heat_indication on house_state.id = heat_indication.house_state_id
         join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
where heat_indication.measure_place = 'CHILDRENS'
group by date_trunc('hour', message_received),
         FLOOR(DATE_PART('minute', message_received) / 10)
order by date DESC, minute DESC;

select date_trunc('hour', message_received)                                      as date,
       round(CAST(float8(avg(heat_indication.temp_celsius)) as numeric), 1)      as temp,
       round(CAST(float8(avg(heat_indication.relative_humidity)) as numeric), 0) as humid,
       round(CAST(float8(avg(air_quality_indication.co2)) as numeric), 0)        as CO2,
       round(CAST(float8(avg(air_quality_indication.iaq)) as numeric), 0)        as IAQ,
       round(CAST(float8(avg(air_quality_indication.voc)) as numeric), 2)        as VOQ
from main.house_state
         join main.heat_indication on house_state.id = heat_indication.house_state_id
         join main.air_quality_indication on house_state.id = air_quality_indication.house_state_id
where heat_indication.measure_place = 'CHILDRENS'
group by date_trunc('hour', message_received)
order by date DESC;