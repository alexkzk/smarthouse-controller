package com.alexsoft.smarthouse.watchdog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WatchdogLogRepository extends JpaRepository<WatchdogLog, Long> {
}
