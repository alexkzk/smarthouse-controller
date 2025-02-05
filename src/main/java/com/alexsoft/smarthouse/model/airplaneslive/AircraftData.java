package com.alexsoft.smarthouse.model.airplaneslive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AircraftData {

    @JsonProperty("ac")
    private List<Aircraft> aircrafts;

    private String msg;

}
