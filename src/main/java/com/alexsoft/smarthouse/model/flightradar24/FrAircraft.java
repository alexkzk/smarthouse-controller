package com.alexsoft.smarthouse.model.flightradar24;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FrAircraft {
    @JsonProperty("fr24_id")
    private String fr24Id;
    private String flight;
    private String callsign;
    private Double lat;
    private Double lon;
    private Integer track;
    private Integer alt;
    private Integer gspeed;
    private Integer vspeed;
    private String squawk;
    private String source;
    private String hex;
    private String type;
    private String reg;
    private String paintedAs;
    private String operatingAs;
    private String origIata;
    private String origIcao;
    private String destIata;
    private String destIcao;
    private String eta;
}
