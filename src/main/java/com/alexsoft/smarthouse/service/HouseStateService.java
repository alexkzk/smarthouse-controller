package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.Aqi;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.Temperature;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_ZONEID;

@Service
public class HouseStateService {
    private final HouseStateRepository houseStateRepository;
    private final HouseStateToDtoMapper houseStateToDtoMapper;

    public HouseStateService(HouseStateRepository houseStateRepository, HouseStateToDtoMapper houseStateToDtoMapper) {
        this.houseStateRepository = houseStateRepository;
        this.houseStateToDtoMapper = houseStateToDtoMapper;
    }

    public List<HouseStateDto> findWithinMinutes(Integer minutes) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes));
        return houseStateToDtoMapper.toDtos(houseStateRepository.findAfter(interval));

    }

    /*public HouseStateDto average(Integer avgPeriodMinutes) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime()
                .minus(Duration.ofMinutes(avgPeriodMinutes));
        List<HouseState> houseStatesToAverage = houseStateRepository.findAfter(interval);
        Map<MeasurePlace, Temperature> temps = houseStatesToAverage.stream().flatMap(h -> h.getTemperatures().stream())
                .collect(Collectors.toMap(Temperature::getMeasurePlace, Function.identity()));
        Map<MeasurePlace, Aqi> aqis = houseStatesToAverage.stream().flatMap(h -> h.getAqis().stream())
                .collect(Collectors.toMap(Aqi::getMeasurePlace, Function.identity()));

    }*/

}
