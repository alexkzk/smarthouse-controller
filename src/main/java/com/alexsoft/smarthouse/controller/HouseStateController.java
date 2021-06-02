package com.alexsoft.smarthouse.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import com.alexsoft.smarthouse.utils.MathUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alexsoft.smarthouse.utils.Constants.SEATTLE_MEASURE_PLACE;
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
        List<HouseState> avg = houseStateService.findWithinInterval(minutes, 0, 0).stream()
                .filter(hst -> !hst.getMeasurePlace().equalsIgnoreCase("UKLN"))
                .filter(hst -> hst.getInOut() == InOut.OUT)
                .collect(Collectors.toList());
        List<HouseState> hourlyAvg = houseStateService.findWithinInterval(0, 1, 0).stream()
                .filter(hst -> hst.getMeasurePlace().equalsIgnoreCase("SEATTLE"))
                .filter(hst -> hst.getInOut() == InOut.OUT)
                .collect(Collectors.toList());
        List<HouseState> terrace = avg.stream().filter(hst -> hst.getMeasurePlace().equalsIgnoreCase("terrace")).collect(Collectors.toList());
        List<HouseState> north = avg.stream().filter(hst -> hst.getMeasurePlace().equalsIgnoreCase("north")).collect(Collectors.toList());
        List<HouseState> seattle = hourlyAvg.stream().filter(hst -> hst.getMeasurePlace().equalsIgnoreCase(SEATTLE_MEASURE_PLACE)).collect(Collectors.toList());
        Long terraceTemp = round(terrace.stream()
                .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null)
                .mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
        Long northTemp = round(north.stream()
                .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null)
                .mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
        Long northAh = round(north.stream()
                .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getAh() != null)
                .mapToDouble(hst -> hst.getAir().getTemp().getAh()).average().orElse(Double.NaN));
        Long seattleTemp = null;
        Long seattleAh = null;
        if (!CollectionUtils.isEmpty(seattle)) {
            seattleTemp = round(seattle.stream()
                    .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null)
                    .mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
            seattleAh = round(seattle.stream()
                    .filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getAh() != null)
                    .mapToDouble(hst -> hst.getAir().getTemp().getAh()).average().orElse(Double.NaN));
        }
        Long avgIaq = round(avg.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getIaq() != null)
                .mapToInt(hst -> hst.getAir().getQuality().getIaq()).average().orElse(Double.NaN));
        Long avgPm25 = round(avg.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getPm25() != null)
                .mapToDouble(hst -> hst.getAir().getQuality().getPm25()).average().orElse(Double.NaN));
        String str = String.format("%d°C/%d [SEA %d°C/%d] - IAQ %d/%d", MathUtils.min(terraceTemp, northTemp), northAh, seattleTemp, seattleAh, avgIaq, avgPm25);
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
