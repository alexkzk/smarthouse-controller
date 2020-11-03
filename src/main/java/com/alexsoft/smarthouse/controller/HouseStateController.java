package com.alexsoft.smarthouse.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@RequestMapping("/measures")
public class HouseStateController {

    private final HouseStateService houseStateService;

    public HouseStateController(HouseStateService houseStateService) {
        this.houseStateService = houseStateService;
    }

    @GetMapping
    public ResponseEntity<List<HouseStateDto>> find(
            @RequestParam @Min(5) @Max(120) Integer minutes,
            @RequestParam @Min(1) @Max(7 * 24) Integer hours
    ) {
        return ResponseEntity.ok(houseStateService.findWithinMinutes(minutes, hours));
    }

    @GetMapping("/avg")
    public ResponseEntity<List<HouseStateDto>> average(
            @RequestParam @Min(5) @Max(120) Integer minutes,
            @RequestParam @Min(1) @Max(7 * 24) Integer hours,
            @RequestParam @Min(5) @Max(60) Integer aggregateIntervalMin
    ) {
        List<HouseStateDto> localDateTimeHouseStateMap = houseStateService.aggregateOnInterval(
                aggregateIntervalMin, minutes, hours);
        return ResponseEntity.ok(localDateTimeHouseStateMap);

    }

}
