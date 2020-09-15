package com.alexsoft.smarthouse.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.Aqi;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Temperature;

import static com.alexsoft.smarthouse.db.entity.MeasurePlace.LIVING_ROOM;
import static com.alexsoft.smarthouse.db.entity.MeasurePlace.TERRACE;
import static com.alexsoft.smarthouse.db.entity.MeasurePlace.TERRACE_WINDOW;

public class HouseStateMsgConverter {

    public static final String MQTT_DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
    public static final ZoneId MQTT_ZONEID = ZoneId.of("Europe/Kiev");

    public static HouseState toEntity(String message) {
        List<String> literals = Arrays.asList(message.split(","));
        Map<String, Double> values = literals.stream().filter(lit -> lit.contains("="))
            .collect(
                Collectors.toMap(
                    lit -> lit.split("=")[0],
                    lit -> Double.valueOf(lit.split("=")[1]))
            );
        HouseState houseState = HouseState.builder()
            .issued(LocalDateTime.parse(literals.get(0), DateTimeFormatter.ofPattern(MQTT_DATE_TIME_PATTERN)))
            .received(ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime())
            .temperatures(Arrays.asList(
                Temperature.builder().measurePlace(LIVING_ROOM).temperature(values.get("livRoomT")).rh(values.get("livRoomRh")).ah(values.get("livRoomAh")).build(),
                Temperature.builder().measurePlace(TERRACE).temperature(values.get("roofT")).rh(values.get("roofRh")).ah(values.get("roofAh")).build(),
                Temperature.builder().measurePlace(TERRACE_WINDOW).temperature(values.get("windT")).rh(values.get("windRh")).ah(values.get("windAh")).build()
            ))
            .aqis(Arrays.asList(
                Aqi.builder().measurePlace(TERRACE).pm10(values.get("pm10")).pm25(values.get("pm25")).build()
            ))
            .build();
//        houseState.setParentForAll();
        return houseState;
    }

}
