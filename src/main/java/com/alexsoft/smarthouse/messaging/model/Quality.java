package com.alexsoft.smarthouse.messaging.model;

import lombok.Data;

@Data
public class Quality {
    private Integer iaq;
    private Double pm25;
    private Double pm10;
    private Meta meta;
}
