select avg(ah) from indication_v2 where indication_v2.indication_place in ('APT2107S-B', 'APT2107S-MB') and local_time > (select (NOW() - INTERVAL '5 hours') - INTERVAL '5 minutes');
