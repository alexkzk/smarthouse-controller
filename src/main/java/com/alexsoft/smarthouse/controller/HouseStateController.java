package com.alexsoft.smarthouse.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import com.alexsoft.smarthouse.utils.MathUtils;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static java.lang.Math.round;

@RestController
@RequestMapping("/v2/measures")
@AllArgsConstructor
public class HouseStateController {

    private final HouseStateService houseStateService;

    @GetMapping("/interval")
    public ResponseEntity<List<HouseState>> findWithinInterval(
        @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(houseStateService.findWithinInterval(minutes, hours, days));
    }

    @GetMapping("/avg")
    public ResponseEntity<String> findWithinInterval(@RequestParam Integer minutes) {
        List<HouseState> avg5min = houseStateService.findWithinInterval(minutes, 0, 0).stream()
                .filter(hst -> !hst.getMeasurePlace().equalsIgnoreCase("UKLN"))
                .filter(hst -> hst.getInOut() == InOut.OUT)
                .collect(Collectors.toList());
        Long terrace = round(avg5min.stream().filter(hst -> hst.getMeasurePlace().equalsIgnoreCase("terrace"))
                .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null)
                .mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
        Long north = round(avg5min.stream().filter(hst -> hst.getMeasurePlace().equalsIgnoreCase("north"))
                .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null)
                .mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
        Long avgIaq = round(avg5min.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getIaq() != null)
                .mapToInt(hst -> hst.getAir().getQuality().getIaq()).average().orElse(Double.NaN));
        Long avgPm25 = round(avg5min.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getPm25() != null)
                .mapToDouble(hst -> hst.getAir().getQuality().getPm25()).average().orElse(Double.NaN));
        String str = String.format("%d°C - IAQ %d/%d", MathUtils.min(terrace, north), avgIaq, avgPm25);
        return ResponseEntity.ok(str);
    }

    @GetMapping
    public ResponseEntity<List<HouseState>> findAll() {
        return ResponseEntity.ok(houseStateService.findAll());
    }

    @GetMapping("/aggregate")
    public ResponseEntity<ChartDto> aggregate() {
        return ResponseEntity.ok(houseStateService.aggregate());
    }


}
