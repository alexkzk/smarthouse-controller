package com.alexsoft.smarthouse.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.Aqi;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Temperature;

import static com.alexsoft.smarthouse.db.entity.MeasurePlace.*;

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
        List<Temperature> temps = new ArrayList<>();
        Temperature livRoom = Temperature.builder().measurePlace(LIVING_ROOM).temperature(values.get("livRoomT")).rh(values.get("livRoomRh")).ah(values.get("livRoomAh")).build();
        Temperature terrace = Temperature.builder().measurePlace(TERRACE).temperature(values.get("roofT")).rh(values.get("roofRh")).ah(values.get("roofAh")).build();
        Temperature terraceWindow = Temperature.builder().measurePlace(TERRACE_WINDOW).temperature(values.get("windT")).rh(values.get("windRh")).ah(values.get("windAh")).build();
        Temperature washRoom = Temperature.builder().measurePlace(WASH_ROOM_1).temperature(values.get("washroom1T")).rh(values.get("washroom1Rh")).ah(values.get("washroom1Ah")).build();
        if(!livRoom.isNull()){
            temps.add(livRoom);
        }
        if(!terrace.isNull()){
            temps.add(terrace);
        }
        if(!terraceWindow.isNull()){
            temps.add(terraceWindow);
        }
        if(!washRoom.isNull()){
            temps.add(washRoom);
        }
        List<Aqi> aqis = new ArrayList<>();
        Aqi aqiTerrace = Aqi.builder().measurePlace(TERRACE).pm10(values.get("pm10")).pm25(values.get("pm25")).build();
        if(!aqiTerrace.isNull()) {
            aqis.add(aqiTerrace);
        }
        return HouseState.builder()
                .issued(LocalDateTime.parse(literals.get(0), DateTimeFormatter.ofPattern(MQTT_DATE_TIME_PATTERN)))
                .received(ZonedDateTime.now(MQTT_ZONEID).toLocalDateTime())
                .temperatures(temps)
                .aqis(aqis)
                .build();
    }

}
