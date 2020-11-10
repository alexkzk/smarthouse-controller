package com.alexsoft.smarthouse.controller;

import java.util.List;

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
    public ResponseEntity<List<HouseStateDto>> findWithinInterval(
            @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(houseStateService.findWithinInterval(minutes, hours, days));
    }

    @GetMapping("/avg")
    public ResponseEntity<List<HouseStateDto>> averageWithinInterval(
            @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(houseStateService.findWithinInterval(minutes, hours, days));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<List<HouseStateDto>> aggregateOnInterval(
            @RequestParam Integer minutes, @RequestParam Integer hours,
            @RequestParam Integer days, @RequestParam @Min(1) @Max(60) Integer aggregateIntervalMin
    ) {
        List<HouseStateDto> localDateTimeHouseStateMap = houseStateService.aggregateOnInterval(
                aggregateIntervalMin, minutes, hours);
        return ResponseEntity.ok(localDateTimeHouseStateMap);

    }

}
