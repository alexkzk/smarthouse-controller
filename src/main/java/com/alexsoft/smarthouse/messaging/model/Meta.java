package com.alexsoft.smarthouse.messaging.model;

import lombok.Data;

@Data
public class Meta {
    private Integer bme680GasResistance;
    private Integer bme680Co2;
    private Double bme680Voc;
    private Integer bme680IaqAccuracy;
    private Integer bme680StaticIaq;
    private Double bme680RawTemp;
    private Integer bme680RawRh;
}
