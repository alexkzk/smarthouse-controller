package com.alexsoft.smarthouse.model.messaging;

import lombok.Data;

@Data
public class Quality {
    private Integer iaq;
    private Double pm25;
    private Double pm10;
    private Meta meta;
}
