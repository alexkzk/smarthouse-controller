package com.alexsoft.smarthouse.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class HourChangeTrackerRepository {

    private final EntityManager entityManager;

    public int getPreviousHour() {
        return (int) entityManager.createNativeQuery("SELECT previous_hour FROM main.hour_change_tracker where id = 1 LIMIT 1").getSingleResult();
    }

    public Object getLastSunsetEvent() {
        return entityManager.createNativeQuery("SELECT updated_at FROM main.hour_change_tracker WHERE id = 2 LIMIT 1").getSingleResult();
    }

    @Transactional
    public void updatePreviousHour(int currentHour) {
        entityManager.createNativeQuery("UPDATE main.hour_change_tracker SET previous_hour = :currentHour, updated_at = NOW() where id = 1")
                .setParameter("currentHour", currentHour)
                .executeUpdate();
    }

    @Transactional
    public void updateLastSunsetEvent(Timestamp lastSunsetEvent) {
        entityManager.createNativeQuery("UPDATE main.hour_change_tracker SET updated_at = :updated_at where id = 2")
                .setParameter("updated_at", lastSunsetEvent)
                .executeUpdate();
    }
}
