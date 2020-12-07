package com.alexsoft.smarthouse.model.messaging;

import lombok.Data;

@Data
public class Air {
    private Temp temp;
    private Quality quality;
    private Pressure pressure;
    private Wind wind;
}
