package com.alexsoft.smarthouse.model.airplaneslive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Aircraft {

    @JsonProperty("hex")
    private String hex;

    @JsonProperty("type")
    private String type;

    @JsonProperty("flight")
    private String flight;

    @JsonProperty("r")
    private String registration;

    @JsonProperty("t")
    private String aircraftType;

    @JsonProperty("desc")
    private String description;

    @JsonProperty("ownOp")
    private String ownerOperator;

    @JsonProperty("year")
    private String year;

    @JsonProperty("alt_baro")
    private String altBaro;

    @JsonProperty("alt_geom")
    private Integer altGeom;

    @JsonProperty("gs")
    private Double groundSpeed;

    @JsonProperty("track")
    private Double track;

    @JsonProperty("baro_rate")
    private Integer baroRate;

    @JsonProperty("squawk")
    private String squawk;

    @JsonProperty("emergency")
    private String emergency;

    @JsonProperty("category")
    private String category;

    @JsonProperty("nav_qnh")
    private Double navQnh;

    @JsonProperty("nav_altitude_mcp")
    private Integer navAltitudeMcp;

    @JsonProperty("nav_heading")
    private Double navHeading;

    @JsonProperty("lat")
    private Double latitude;

    @JsonProperty("lon")
    private Double longitude;

    @JsonProperty("nic")
    private Integer nic;

    @JsonProperty("rc")
    private Integer rc;

    @JsonProperty("seen_pos")
    private Double seenPos;

    @JsonProperty("recentReceiverIds")
    private List<String> recentReceiverIds;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("nic_baro")
    private Integer nicBaro;

    @JsonProperty("nac_p")
    private Integer nacP;

    @JsonProperty("nac_v")
    private Integer nacV;

    @JsonProperty("sil")
    private Integer sil;

    @JsonProperty("sil_type")
    private String silType;

    @JsonProperty("gva")
    private Integer gva;

    @JsonProperty("sda")
    private Integer sda;

    @JsonProperty("alert")
    private Integer alert;

    @JsonProperty("spi")
    private Integer spi;

    @JsonProperty("mlat")
    private List<String> mlat;

    @JsonProperty("tisb")
    private List<String> tisb;

    @JsonProperty("messages")
    private Integer messages;

    @JsonProperty("seen")
    private Double seen;

    @JsonProperty("rssi")
    private Double rssi;

    @JsonProperty("dst")
    private Double distance;

    @JsonProperty("dir")
    private Double direction;

}
