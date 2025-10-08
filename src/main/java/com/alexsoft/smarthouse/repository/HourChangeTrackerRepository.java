package com.alexsoft.smarthouse.repository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class HourChangeTrackerRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(HourChangeTrackerRepository.class);


    private final EntityManager entityManager;

    public Integer getPreviousHour() {
        try {
            return (int) entityManager.createNativeQuery("SELECT previous_hour FROM main.hour_change_tracker where id = 1 LIMIT 1").getSingleResult();
        } catch (Exception e) {
            LOGGER.error("Error getting previous hour", e);
            return null;
        }
    }

    public Object getLastSunsetEvent() {
        try {
            return entityManager.createNativeQuery("SELECT updated_at FROM main.hour_change_tracker WHERE id = 2 LIMIT 1").getSingleResult();
        } catch (Exception e) {
            LOGGER.error("Error getting last sunset event", e);
            return null;
        }
    }

    @Transactional
    public void updatePreviousHour(int currentHour, Timestamp updatedAt) {
        entityManager.createNativeQuery("UPDATE main.hour_change_tracker SET previous_hour = :currentHour, updated_at = :updated_at where id = 1")
                .setParameter("currentHour", currentHour)
                .setParameter("updated_at", updatedAt)
                .executeUpdate();
    }

    @Transactional
    public void updateLastSunsetEvent(Timestamp lastSunsetEvent, boolean upsert) {
        if (upsert) {
            entityManager.createNativeQuery("INSERT INTO main.hour_change_tracker (id, updated_at) values (2, :updated_at)")
                    .setParameter("updated_at", lastSunsetEvent)
                    .executeUpdate();
        } else {
            entityManager.createNativeQuery("UPDATE main.hour_change_tracker SET updated_at = :updated_at where id = 2")
                    .setParameter("updated_at", lastSunsetEvent)
                    .executeUpdate();
        }
    }
}
