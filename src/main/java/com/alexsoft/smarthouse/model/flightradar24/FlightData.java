package com.alexsoft.smarthouse.model.flightradar24;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FlightData {
    @JsonProperty("data")
    List<FrAircraft> frAircrafts;
}
