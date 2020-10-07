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
    public ResponseEntity<List<HouseStateDto>> find(@RequestParam @Min(5) @Max(120) Integer withinMinutes) {
        return ResponseEntity.ok(houseStateService.findWithinMinutes(withinMinutes));
    }

    /*@GetMapping("/avg")
    public ResponseEntity<List<HouseStateDto>> average(@RequestParam @Min(5) @Max(120) Integer withinMinutes) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime()
                .minus(Duration.ofMinutes(withinMinutes));
        return ResponseEntity.ok(houseStateToDtoMapper.toDtos(houseStateRepository.findAfter(interval)));

    }*/

}
