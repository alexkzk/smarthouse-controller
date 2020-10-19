package com.alexsoft.smarthouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;
import org.junit.Test;

import static com.alexsoft.smarthouse.db.entity.MeasurePlace.LIVING_ROOM;
import static com.alexsoft.smarthouse.db.entity.MeasurePlace.TERRACE_ROOF;
import static com.alexsoft.smarthouse.db.entity.MeasurePlace.TERRACE_WINDOW;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HouseStateBuilderTest {

    @Test
    public void HouseStateBuildingWithSettingParentsTest() {
        HouseState houseState = HouseState.builder()
            .heatIndications(Arrays.asList(
                HeatIndication.builder().measurePlace(LIVING_ROOM).tempCelsius(0.0F).relativeHumidity(0).absoluteHumidity(0.0F).build(),
                HeatIndication.builder().measurePlace(TERRACE_ROOF).tempCelsius(0.0F).relativeHumidity(0).absoluteHumidity(0.0F).build(),
                HeatIndication.builder().measurePlace(TERRACE_WINDOW).tempCelsius(0.0F).relativeHumidity(0).absoluteHumidity(0.0F).build()
            ))
            .airQualities(Collections.singletonList(
                AirQualityIndication.builder().measurePlace(TERRACE_ROOF).pm10(0.0F).pm25(0.0F).build()
            ))
            .windIndications(new ArrayList<>())
            .build();
        houseState.getAirQualities().forEach(aqi -> assertEquals(aqi.getHouseState(), houseState));
        houseState.getHeatIndications().forEach(temp -> assertEquals(temp.getHouseState(), houseState));

    }


}
