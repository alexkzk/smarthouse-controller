package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.repository.InfluxRepository;
import com.alexsoft.smarthouse.service.IndicationServiceV2;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/indications")
@AllArgsConstructor
public class IndicationsController {

    private final IndicationServiceV2 indicationServiceV2;
    private final InfluxRepository influxRepository;

    @GetMapping("/aggregate")
    public ResponseEntity<String> aggregate(@RequestParam String period, @RequestParam(required = false) Integer greaterThanId) {
        int aggregated = indicationServiceV2.aggregate(period, greaterThanId);
        return ResponseEntity.ok("Aggregated " + aggregated + " indications");
    }



    @PostMapping("/influx-sync")
    public ResponseEntity<String> filterByDate(@RequestParam LocalDateTime startDate, @RequestParam LocalDateTime endDate) {
        influxRepository.syncFromPostgres(startDate, endDate);
        return null;

    }

}
