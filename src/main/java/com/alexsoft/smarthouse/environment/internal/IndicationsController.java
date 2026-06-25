package com.alexsoft.smarthouse.environment.internal;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.alexsoft.smarthouse.core.util.DateUtils.toLocalDateTime;

/**
 * REST Controller for managing and processing sensor indications.
 * Provides endpoints for creating indications, reprocessing CSV data, and synchronizing with InfluxDB.
 */
@RestController
@RequestMapping("/indications")
@AllArgsConstructor
public class IndicationsController {

    private final Optional<InfluxRepository> influxRepository;

    /**
     * Synchronizes data from PostgreSQL to InfluxDB for a specified date range.
     *
     * @param startDate The start date for synchronization (optional).
     * @param endDate   The end date for synchronization (optional).
     * @return Null response for now.
     */
    @PostMapping("/influx-sync")
    public ResponseEntity<String> filterByDate(@RequestParam(required = false) LocalDateTime startDate,
                                               @RequestParam(required = false) LocalDateTime endDate) {
        influxRepository.ifPresent(r -> r.syncAllFromPostgresBy1Days(startDate, endDate));
        return null;
    }
}
