package com.alexsoft.smarthouse.rest;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alexsoft.smarthouse.utils.MathUtils.round;

@RestController
@RequestMapping("/status")
@AllArgsConstructor
public class HouseStateStatusRestController {

    private final HouseStateService houseStateService;

    @GetMapping("/average")
    public ResponseEntity<List<HouseState>> findWithinInterval(
        @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(houseStateService.findWithinInterval(minutes, hours, days));
    }

    @GetMapping("/average/short")
    public ResponseEntity<String> findWithinInterval(@RequestParam Integer minutes) {
        return ResponseEntity.ok(houseStateService.getOutsideStatus(minutes));
    }

    @GetMapping
    public ResponseEntity<List<HouseState>> findAll() {
        return ResponseEntity.ok(houseStateService.findAll());
    }

    @GetMapping("/aggregate")
    public ResponseEntity<ChartDto> aggregate() {
        return ResponseEntity.ok(houseStateService.getAggregatedData());
    }
}
