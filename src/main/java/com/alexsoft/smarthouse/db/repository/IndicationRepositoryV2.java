package com.alexsoft.smarthouse.db.repository;

import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;

@Repository
public class IndicationRepositoryV2 {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Map<String, Object>> getAggregatedData(List<String> places, String period) {
        String selectBase = "SELECT received_local AS msg_received, " +
                "       in_out, " +
                "       indication_place, " +
                "       aggregation_period AS period, " +
                "       ROUND(CAST(FLOAT8(celsius) AS numeric), 1) AS temp, " +
                "       ROUND(CAST(FLOAT8(rh) AS numeric), 0) AS rh, " +
                "       ROUND(CAST(FLOAT8(ah) AS numeric), 1) AS ah, " +
                "       ROUND(CAST(FLOAT8(mm_hg) AS numeric), 0) AS mm_hg, " +
                "       ROUND(CAST(FLOAT8(direction) AS numeric), 0) AS direction, " +
                "       ROUND(CAST(FLOAT8(speed_ms) AS numeric), 0) AS speed_ms " +
                "FROM main.indication " +
                "LEFT JOIN main.air a ON a.id = indication.air_id " +
                "LEFT JOIN main.air_temp_indication t ON t.id = a.temp_id " +
                "LEFT JOIN main.air_pressure_indication ap ON ap.id = a.pressure_id " +
                "LEFT JOIN main.air_wind_indication w ON w.id = a.wind_id ";

        String whereBase = "WHERE aggregation_period LIKE :period ";

        if (places != null && !places.isEmpty()) {
            whereBase += "AND indication_place = ANY(:places) ";
        }

        String timeCondition;

        if ("DAILY".equalsIgnoreCase(period)) {
            timeCondition = "AND DATE_PART('day', AGE(now() at time zone 'utc', received_local)) <= 30 " +
                    "AND DATE_PART('month', AGE(now() at time zone 'utc', received_local)) = 0 " +
                    "AND DATE_PART('year', AGE(now() at time zone 'utc', received_local)) = 0 ";
        } else if ("MONTHLY".equalsIgnoreCase(period)) {
            timeCondition = "";
        } else {
            period = "HOURLY";
            timeCondition = "AND date_trunc('day', received_local) = date_trunc('day', now() at time zone 'utc') " +
                    "AND date_trunc('month', received_local) = date_trunc('month', now() at time zone 'utc') " +
                    "AND date_trunc('year', received_local) = date_trunc('year', now() at time zone 'utc') " +
                    "AND DATE_PART('hour', now() at time zone 'utc') - DATE_PART('hour', received_local) <= 1 " +
                    "AND aggregation_period = 'MINUTELY' " +
                    "UNION ALL " +
                    selectBase + whereBase +
                    "AND DATE_PART('day', AGE(now() at time zone 'utc', received_local)) <= 5 " +
                    "AND DATE_PART('month', AGE(now() at time zone 'utc', received_local)) = 0 " +
                    "AND DATE_PART('year', AGE(now() at time zone 'utc', received_local)) = 0 " +
                    "AND aggregation_period = 'HOURLY' ";
        }

        String orderBase = "ORDER BY msg_received DESC";

        String sql = selectBase + whereBase + timeCondition + orderBase;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("period", period);

        if (places != null && !places.isEmpty()) {
            query.setParameter("places", places.toArray(new String[0]));
        }

        // Tell Hibernate to transform the result set into a list of maps (TupleBackedMap)
        NativeQuery<?> nativeQuery = query.unwrap(NativeQuery.class);
        nativeQuery.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

        return (List<Map<String, Object>>) nativeQuery.getResultList();
    }
}
