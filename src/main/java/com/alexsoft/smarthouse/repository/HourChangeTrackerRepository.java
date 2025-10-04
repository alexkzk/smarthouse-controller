package com.alexsoft.smarthouse.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Repository
@RequiredArgsConstructor
public class HourChangeTrackerRepository {

    private final EntityManager entityManager;

    public int getPreviousHour() {
        return (int) entityManager.createNativeQuery(
                "SELECT previous_hour FROM main.hour_change_tracker LIMIT 1"
        ).getSingleResult();
    }

    @Transactional
    public void updatePreviousHour(int currentHour) {
        entityManager.createNativeQuery(
                        "UPDATE main.hour_change_tracker SET previous_hour = :currentHour, updated_at = NOW()"
                )
                .setParameter("currentHour", currentHour)
                .executeUpdate();
    }
}
