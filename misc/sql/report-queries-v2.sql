select date_trunc('hour', message_received) +
       cast((FLOOR(DATE_PART('minute', message_received) / 60) * 60 || 'minutes') as interval) + interval '2h' as msg_received,
       in_out,
       measure_place,
       round(CAST(float8(avg(celsius)) as numeric), 1)   as temp,
       round(CAST(float8(avg(rh)) as numeric), 0)        as rh,
       round(CAST(float8(avg(ah)) as numeric), 1)        as ah,
       round(CAST(float8(avg(mm_hg)) as numeric), 0)     as mm_hg,
       round(CAST(float8(avg(pm10)) as numeric), 1)      as pm10,
       round(CAST(float8(avg(pm25)) as numeric), 1)      as pm25,
       round(CAST(float8(avg(iaq)) as numeric), 0)       as iaq,
       round(CAST(float8(max(iaq)) as numeric), 0)       as iaq_max,
       round(CAST(float8(max(direction)) as numeric), 0) as direction,
       round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms
from main.house_state_v2
         left join main.air a on a.id = house_state_v2.air_id
         left join main.air_temp t on t.id = a.temp_id
         left join main.air_pressure ap on ap.id = a.pressure_id
         left join main.air_quality aq on a.quality_id = aq.id
         left join main.wind w on w.id = a.wind_id
where DATE_PART('hour', now()) - DATE_PART('hour', message_received) > 1
  AND DATE_PART('day', now()) - DATE_PART('day', message_received) <= 5
group by msg_received, in_out, measure_place
union all
select date_trunc('hour', message_received) +
       cast((FLOOR(DATE_PART('minute', message_received) / 5) * 5 || 'minutes') as interval) + interval '2h' as msg_received,
       in_out,
       measure_place,
       round(CAST(float8(avg(celsius)) as numeric), 1)   as temp,
       round(CAST(float8(avg(rh)) as numeric), 0)        as rh,
       round(CAST(float8(avg(ah)) as numeric), 1)        as ah,
       round(CAST(float8(avg(mm_hg)) as numeric), 0)     as mm_hg,
       round(CAST(float8(avg(pm10)) as numeric), 1)      as pm10,
       round(CAST(float8(avg(pm25)) as numeric), 1)      as pm25,
       round(CAST(float8(avg(iaq)) as numeric), 0)       as iaq,
       round(CAST(float8(max(iaq)) as numeric), 0)       as iaq_max,
       round(CAST(float8(max(direction)) as numeric), 0) as direction,
       round(CAST(float8(max(speed_ms)) as numeric), 0)  as speed_ms
from main.house_state_v2
         left join main.air a on a.id = house_state_v2.air_id
         left join main.air_temp t on t.id = a.temp_id
         left join main.air_pressure ap on ap.id = a.pressure_id
         left join main.air_quality aq on a.quality_id = aq.id
         left join main.wind w on w.id = a.wind_id
where date_trunc('day', message_received) = date_trunc('day', now())
  AND DATE_PART('hour', now()) - DATE_PART('hour', message_received) >= 0
  and DATE_PART('hour', now()) - DATE_PART('hour', message_received) <= 1
group by msg_received, in_out, measure_place
union
select date_trunc('day', message_received + interval '2h') as msg_received,
       in_out,
       measure_place,
       round(CAST(float8(avg(celsius)) as numeric), 1)     as temp,
       round(CAST(float8(avg(rh)) as numeric), 0)          as rh,
       round(CAST(float8(avg(ah)) as numeric), 1)          as ah,
       round(CAST(float8(avg(mm_hg)) as numeric), 0)       as mm_hg,
       round(CAST(float8(avg(pm10)) as numeric), 1)        as pm10,
       round(CAST(float8(avg(pm25)) as numeric), 1)        as pm25,
       round(CAST(float8(avg(iaq)) as numeric), 0)         as iaq,
       round(CAST(float8(max(iaq)) as numeric), 0)         as iaq_max,
       round(CAST(float8(max(direction)) as numeric), 0)   as direction,
       round(CAST(float8(max(speed_ms)) as numeric), 0)    as speed_ms
from main.house_state_v2
         left join main.air a on a.id = house_state_v2.air_id
         left join main.air_temp t on t.id = a.temp_id
         left join main.air_pressure ap on ap.id = a.pressure_id
         left join main.air_quality aq on a.quality_id = aq.id
         left join main.wind w on w.id = a.wind_id
where DATE_PART('day', now()) - DATE_PART('day', message_received) > 5
  and DATE_PART('day', now()) - DATE_PART('day', message_received) <= 15
group by date_trunc('day', message_received + interval '2h'), in_out, measure_place
order by msg_received DESC;