package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.service.IndicationServiceV2;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/indications")
@AllArgsConstructor
public class IndicationsRestController {

    private final IndicationServiceV2 indicationServiceV2;

    @GetMapping("/aggregate")
    public ResponseEntity<String> aggregate(@RequestParam String period, @RequestParam(required = false) Integer greaterThanId) {
        int aggregated = indicationServiceV2.aggregate(period, greaterThanId);
        return ResponseEntity.ok("Aggregated " + aggregated + " indications");
    }
}
