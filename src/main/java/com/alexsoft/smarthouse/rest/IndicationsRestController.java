package com.alexsoft.smarthouse.rest;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.service.IndicationService;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestParam Integer interval, @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days, HttpServletRequest request
    ) {
        return ResponseEntity.ok(indicationService.aggregateOnInterval(interval, ChronoUnit.MINUTES, minutes, hours, days, request.getRemoteAddr(), request.getServletPath()));
    }

    @GetMapping("/average/short")
    public ResponseEntity<String> findWithinInterval(HttpServletRequest request) {
        return ResponseEntity.ok(indicationService.getHourlyAveragedShortStatus(request.getRemoteAddr(), request.getServletPath()));
    }

    @GetMapping("/average/outTemp")
    public ResponseEntity<String> getAverageChornomorskTemp(HttpServletRequest request) {
        return ResponseEntity.ok("{temp: " + indicationService.getAverageChornomorskTemp(request.getRemoteAddr(), request.getServletPath()) + "}");
    }

    @GetMapping
    public ResponseEntity<List<Indication>> findAll(HttpServletRequest request) {
        return ResponseEntity.ok(indicationService.findRecent(request.getRemoteAddr(), request.getServletPath()));
    }

    @GetMapping(params = "startDate")
    public ResponseEntity<List<Indication>> findAfter(@RequestParam String startDate,
                                                      @RequestParam(required = false) AggregationPeriod period,
                                                      @RequestParam(required = false) String place, HttpServletRequest request) {
        return ResponseEntity.ok(indicationService.findAfter(startDate, period, place, request.getRemoteAddr(), request.getServletPath(), request.getServletPath()));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<ChartDto> aggregate(HttpServletRequest request) {
        return ResponseEntity.ok(indicationService.getAggregatedData(request.getRemoteAddr(), request.getServletPath()));
    }
}
