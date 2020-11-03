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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alexsoft.smarthouse.db.entity.MeasurePlace.*;

public class HouseStateMsgConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HouseStateMsgConverter.class);

    public static final String MQTT_DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
    public static final ZoneId MQTT_PRODUCER_TIMEZONE_ID = ZoneId.of("Europe/Kiev");

    public static HouseState toEntity(String message) {
        List<String> literals = Arrays.asList(message.split(","));
        Map<String, Float> values = literals.stream().filter(lit -> lit.contains("=")).collect(
                        Collectors.toMap(lit -> lit.split("=")[0], HouseStateMsgConverter::getaFloat));

        List<HeatIndication> temps = new ArrayList<>();

        HeatIndication childRoom = HeatIndication.builder().measurePlace(CHILDRENS).tempCelsius(values.get("childrenTemp"))
            .relativeHumidity(getIntValue(values, "childrenHumid")).build();

        HeatIndication livRoom = HeatIndication.builder().measurePlace(LIVING_ROOM).tempCelsius(values.get("livRoomT"))
            .relativeHumidity(getIntValue(values, "livRoomRh")).absoluteHumidity(values.get("livRoomAh")).build();

        HeatIndication terrace = HeatIndication.builder().measurePlace(TERRACE_ROOF).tempCelsius(values.get("roofT"))
            .relativeHumidity(getIntValue(values, "roofRh")).absoluteHumidity(values.get("roofAh")).build();

        HeatIndication terraceWindow = HeatIndication.builder().measurePlace(TERRACE_WINDOW).tempCelsius(values.get("windT"))
            .relativeHumidity(getIntValue(values, "windRh")).absoluteHumidity(values.get("windAh")).build();

        HeatIndication balconyIndications = HeatIndication.builder().measurePlace(BALCONY).tempCelsius(values.get("balcT"))
            .relativeHumidity(getIntValue(values, "balcRh")).absoluteHumidity(values.get("balcAh")).build();

        if(!livRoom.isNull()){
            temps.add(livRoom);
        }
        if(!childRoom.isNull()){
            temps.add(childRoom);
        }
        if(!terrace.isNull()){
            temps.add(terrace);
        }
        if(!terraceWindow.isNull()){
            temps.add(terraceWindow);
        }
        if(!balconyIndications.isNull()){
            temps.add(balconyIndications);
        }

        List<AirQualityIndication> airQualities = new ArrayList<>();
        AirQualityIndication airQualityIndicationTerrace = AirQualityIndication.builder().measurePlace(TERRACE_ROOF).pm10(values.get("pm10")).pm25(values.get("pm25")).build();
        if(!airQualityIndicationTerrace.isNull()) {
            airQualities.add(airQualityIndicationTerrace);
        }
        AirQualityIndication airQualityIndicationChildrens = AirQualityIndication.builder().measurePlace(CHILDRENS)
            .iaq(values.get("childrenIaq")).co2(values.get("childrenCo2")).voc(values.get("childrenVoc")).build();
        if(!airQualityIndicationChildrens.isNull()) {
            airQualities.add(airQualityIndicationChildrens);
        }
        return HouseState.builder()
                .messageIssued(LocalDateTime.parse(literals.get(0), DateTimeFormatter.ofPattern(MQTT_DATE_TIME_PATTERN)))
                .messageReceived(ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime())
                .heatIndications(temps)
                .airQualities(airQualities)
                .windIndications(new ArrayList<>())
                .build();
    }

    private static Integer getIntValue(final Map<String, Float> values, final String valueKey) {
        return values.get(valueKey) == null || Float.isNaN(values.get(valueKey)) ? null : values.get(valueKey).intValue();
    }

    private static Float getaFloat(final String lit) {
        String s = null;
        try {
            s = lit.split("=")[1];
            return s.equals("nan") ? Float.NaN : Float.parseFloat(s);
        } catch (Exception e) {
            LOGGER.error("Couldn't convert" + s + "to a float", e);
            return Float.NaN;
        }
    }

}
