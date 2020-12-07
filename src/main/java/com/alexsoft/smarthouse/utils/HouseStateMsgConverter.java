package com.alexsoft.smarthouse.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.v1.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.v1.HeatIndication;
import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.v1.WindIndication;
import com.alexsoft.smarthouse.model.metar.Metar;
import com.alexsoft.smarthouse.service.MetarReceiver;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.db.entity.v1.MeasurePlace.*;

@Service
@AllArgsConstructor
public class HouseStateMsgConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HouseStateMsgConverter.class);

    public static final String MQTT_DATE_TIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
    public static final ZoneId MQTT_PRODUCER_TIMEZONE_ID = ZoneId.of("Europe/Kiev");

    private final TempUtils tempUtils = new TempUtils();
    private final MetarReceiver metarReceiver;

    public HouseState toEntity(String message) {
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
            .staticIaq(values.get("childrenSIaq"))
            .iaq(values.get("childrenIaq"))
            .iaqAccuracy(values.get("childrenIaqAccuracy") != null ? values.get("childrenIaqAccuracy").intValue() : null)
            .gasResistance(values.get("gasResistance"))
            .co2(values.get("childrenCo2")).voc(values.get("childrenVoc")).build();
        if(!airQualityIndicationChildrens.isNull()) {
            airQualities.add(airQualityIndicationChildrens);
        }
        HouseState build = HouseState.builder()
                .messageIssued(LocalDateTime.parse(literals.get(0), DateTimeFormatter.ofPattern(MQTT_DATE_TIME_PATTERN)))
                .messageReceived(ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime())
                .heatIndications(temps)
                .airQualities(airQualities)
                .windIndications(new ArrayList<>())
                .build();

        if (message.contains("pm25")) { // todo replace this condition by adding measure place in message
            try {
                Metar metar = metarReceiver.getMetar();
                if (metarIsNotExpired(metar)) {
                    Float temp = Float.valueOf(metar.getTemperature().getValue());
                    Integer devpoint = metar.getDewpoint().getValue();
                    Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
                    HeatIndication heatIndication = HeatIndication.builder()
                            .measurePlace(MeasurePlace.CHERNIVTSI_AIRPORT)
                            .tempCelsius(temp)
                            .relativeHumidity(rh)
                            .absoluteHumidity(tempUtils.calculateAbsoluteHumidity(temp, rh))
                            .build();
                    build.addIndication(heatIndication);
                    if((metar.getWindDirection() != null && metar.getWindDirection().getValue() != null) ||
                            (metar.getWindSpeed() != null && metar.getWindSpeed().getValue() != null)) {
                        WindIndication windIndication = WindIndication.builder()
                                .direction(metar.getWindDirection() == null ? null : metar.getWindDirection().getValue())
                                .speed(metar.getWindSpeed() == null ? null : metar.getWindSpeed().getValue())
                                .measurePlace(MeasurePlace.CHERNIVTSI_AIRPORT)
                                .build();
                        build.addIndication(windIndication);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Couldn't retrieve a metar", e);
            }
        }

        return build;
    }

    private boolean metarIsNotExpired(final Metar metar) {
        return metar != null && metar.getTime() != null && metar.getTime().getIssueDateTime() != null &&
                ChronoUnit.HOURS.between(metar.getTime().getIssueDateTime(), ZonedDateTime.now()) < 1;
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
