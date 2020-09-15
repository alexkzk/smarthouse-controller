package com.alexsoft.smarthouse.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_ZONEID;

@RestController
@RequestMapping("/alexhome/measures")
public class HouseStateController {
    private final HouseStateRepository houseStateRepository;
    private final HouseStateToDtoMapper houseStateToDtoMapper;

    public HouseStateController(
        final HouseStateRepository houseStateRepository,
        final HouseStateToDtoMapper houseStateToDtoMapper
    ) {
        this.houseStateRepository = houseStateRepository;
        this.houseStateToDtoMapper = houseStateToDtoMapper;
    }

    @GetMapping
    public ResponseEntity<List<HouseStateDto>> findAll() {
        LocalDateTime interval = ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime().minus(Duration.ofMinutes(15));
        return ResponseEntity.ok(houseStateToDtoMapper.toDtos(houseStateRepository.findBefore(interval)));
    }

}
