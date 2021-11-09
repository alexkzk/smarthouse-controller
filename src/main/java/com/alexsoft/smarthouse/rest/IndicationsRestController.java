package com.alexsoft.smarthouse.rest;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.service.IndicationService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/indications")
@AllArgsConstructor
public class IndicationsRestController {

    private final IndicationService indicationService;

    @GetMapping("/average")
    public ResponseEntity<List<Indication>> findWithinInterval(
            @RequestParam Integer interval, @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(indicationService.aggregateOnInterval(interval, ChronoUnit.MINUTES, minutes, hours, days));
    }

    @GetMapping("/average/short")
    public ResponseEntity<String> findWithinInterval() {
        return ResponseEntity.ok(indicationService.getHourlyAveragedShortStatus());
    }

    @GetMapping
    public ResponseEntity<List<Indication>> findAll() {
        return ResponseEntity.ok(indicationService.findAll());
    }

    @GetMapping(params = "startDate")
    public ResponseEntity<List<Indication>> findAfter(@RequestParam String startDate,
                                                      @RequestParam(required = false) AggregationPeriod period,
                                                      @RequestParam(required = false) String place) {
        return ResponseEntity.ok(indicationService.findAfter(startDate, period, place));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<ChartDto> aggregate() {
        return ResponseEntity.ok(indicationService.getAggregatedData());
    }
}
