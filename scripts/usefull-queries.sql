select avg(ah) from indication_v2 where indication_v2.indication_place in ('APT2107S-B', 'APT2107S-MB') and local_time > (select (NOW() - INTERVAL '5 hours') - INTERVAL '5 minutes');

select i.aggregation_period, ati.celsius, ati.ah
from indication i
         left join main.air a on i.air_id = a.id
         left join main.air_temp_indication ati on a.temp_id = ati.id
where i.indication_place = 'DEHUMIDIFIER' order by i.id desc;


SELECT DATE(local_time) AS day, COUNT(*) AS minutes_on FROM indication_v2 WHERE temp = 10 and indication_place = 'DEHUMIDIFIER'
GROUP BY DATE(local_time) ORDER BY day desc;

SELECT DATE(local_time) AS day, EXTRACT(HOUR FROM local_time) AS hour, COUNT(*) AS minutes_on FROM indication_v2 WHERE temp = 10
AND indication_place = 'DEHUMIDIFIER' GROUP BY DATE(local_time), EXTRACT(HOUR FROM local_time) ORDER BY day DESC, hour DESC;

-- KFXE TRAFFIC
SELECT
    (DATE_TRUNC('hour', timestamp - INTERVAL '5 hours') + INTERVAL '10 minutes' * FLOOR(EXTRACT(MINUTE FROM (timestamp + INTERVAL '5 hours')) / 10)) AS interval_start,
    ROUND(AVG(airborne_aircrafts), 1) AS average_airborne_aircrafts,
    airspace
FROM
    airspace_activity
GROUP BY
    interval_start, airspace
ORDER BY
    interval_start desc;
