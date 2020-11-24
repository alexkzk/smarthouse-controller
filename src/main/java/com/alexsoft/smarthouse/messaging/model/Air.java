package com.alexsoft.smarthouse.messaging.model;

import lombok.Data;

@Data
public class Air {
    private Temp temp;
    private Quality quality;
    private Pressure pressure;
    private Wind wind;
}
