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

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;

import static com.alexsoft.smarthouse.db.entity.MeasurePlace.*;

public class HouseStateMsgConverter {

    public static final String MQTT_DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
    public static final ZoneId MQTT_PRODUCER_TIMEZONE_ID = ZoneId.of("Europe/Kiev");

    public static HouseState toEntity(String message) {
        List<String> literals = Arrays.asList(message.split(","));
        Map<String, Float> values = literals.stream().filter(lit -> lit.contains("="))
                .collect(
                        Collectors.toMap(
                                lit -> lit.split("=")[0],
                                lit -> Float.valueOf(lit.split("=")[1]))
                );

        List<HeatIndication> temps = new ArrayList<>();

        HeatIndication livRoom = HeatIndication.builder().measurePlace(LIVING_ROOM).tempCelsius(values.get("livRoomT"))
            .relativeHumidity(values.get("livRoomRh") == null ? null : values.get("livRoomRh").intValue())
            .absoluteHumidity(values.get("livRoomAh")).build();

        HeatIndication terrace = HeatIndication.builder().measurePlace(TERRACE_ROOF).tempCelsius(values.get("roofT"))
            .relativeHumidity(values.get("roofRh") == null ? null : values.get("roofRh").intValue())
            .absoluteHumidity(values.get("roofAh")).build();

        HeatIndication terraceWindow = HeatIndication.builder().measurePlace(TERRACE_WINDOW).tempCelsius(values.get("windT"))
            .relativeHumidity(values.get("windRh") == null ? null : values.get("windRh").intValue())
            .absoluteHumidity(values.get("windAh")).build();

        HeatIndication washRoom = HeatIndication.builder().measurePlace(WASH_ROOM_1).tempCelsius(values.get("washroom1T"))
            .relativeHumidity(values.get("washroom1Rh") == null ? null : values.get("washroom1Rh").intValue())
            .absoluteHumidity(values.get("washroom1Ah")).build();

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

        List<AirQualityIndication> airQualities = new ArrayList<>();
        AirQualityIndication airQualityIndicationTerrace = AirQualityIndication.builder().measurePlace(TERRACE_ROOF).pm10(values.get("pm10")).pm25(values.get("pm25")).build();
        if(!airQualityIndicationTerrace.isNull()) {
            airQualities.add(airQualityIndicationTerrace);
        }
        return HouseState.builder()
                .messageIssued(LocalDateTime.parse(literals.get(0), DateTimeFormatter.ofPattern(MQTT_DATE_TIME_PATTERN)))
                .messageReceived(ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime())
                .heatIndications(temps)
                .airQualities(airQualities)
                .windIndications(new ArrayList<>())
                .build();
    }

}
