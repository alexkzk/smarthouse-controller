package com.alexsoft.smarthouse.watchdog;

import com.alexsoft.smarthouse.watchdog.WatchdogLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WatchdogLogRepository extends JpaRepository<WatchdogLog, Long> {
}
