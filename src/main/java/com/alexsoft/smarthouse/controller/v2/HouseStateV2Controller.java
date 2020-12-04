package com.alexsoft.smarthouse.controller.v2;

import java.util.List;

import com.alexsoft.smarthouse.dto.v2.HouseStateV2Dto;
import com.alexsoft.smarthouse.service.HouseStateV2Service;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/measures")
@AllArgsConstructor
public class HouseStateV2Controller {

    private final HouseStateV2Service houseStateV2Service;


    @GetMapping("/interval")
    public ResponseEntity<List<HouseStateV2Dto>> findWithinInterval(
        @RequestParam Integer minutes, @RequestParam Integer hours, @RequestParam Integer days
    ) {
        return ResponseEntity.ok(houseStateV2Service.findWithinInterval(minutes, hours, days));
    }


    @GetMapping
    public ResponseEntity<List<HouseStateV2Dto>> findAll() {
        return ResponseEntity.ok(houseStateV2Service.findAll());
    }


}
