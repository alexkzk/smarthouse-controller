select message_issued, message_received, celsius, rh, ah
from main.house_state_v2
         left join main.air_temp a on a.id = house_state_v2.temp_id
where measure_place = 'IN-TERRACE'
order by message_received DESC;
select message_issued, message_received, celsius, rh, ah
from main.house_state_v2
         left join main.air_temp a on a.id = house_state_v2.temp_id
where measure_place = 'IN-TERRACE'
order by message_received DESC;