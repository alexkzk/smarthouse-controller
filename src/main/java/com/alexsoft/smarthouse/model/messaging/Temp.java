package com.alexsoft.smarthouse.model.messaging;

import lombok.Data;

@Data
public class Temp {
    private Double celsius;
    private Integer rh;
    private Double ah;
}
