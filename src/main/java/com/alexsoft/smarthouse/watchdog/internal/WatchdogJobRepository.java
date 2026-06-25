package com.alexsoft.smarthouse.watchdog;

import com.alexsoft.smarthouse.watchdog.WatchdogJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchdogJobRepository extends JpaRepository<WatchdogJob, Long> {
    List<WatchdogJob> findByEnabledTrue();
}
