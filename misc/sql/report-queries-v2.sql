select date_trunc('hour', message_received) + (FLOOR(DATE_PART('minute', message_received) / :interval_min)
                                                   * :interval_min || 'minutes')::interval +
       interval '2h'                                   as msg_received,
       round(CAST(float8(avg(celsius)) as numeric), 1) as temp,
       round(CAST(float8(avg(rh)) as numeric), 1)      as rh,
       round(CAST(float8(avg(ah)) as numeric), 1)      as ah
from main.house_state_v2
         left join main.air a on a.id = house_state_v2.air_id
         left join main.air_temp t on t.id = a.temp_id
where measure_place = 'IN-TERRACE'
group by date_trunc('hour', message_received) +
         (FLOOR(DATE_PART('minute', message_received) / :interval_min) * :interval_min || 'minutes')::interval +
         interval '2h'
order by msg_received DESC;