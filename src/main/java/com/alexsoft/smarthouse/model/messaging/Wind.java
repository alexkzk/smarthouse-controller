package com.alexsoft.smarthouse.model.messaging;

import lombok.Data;

@Data
public class Wind {
    private Integer direction;
    private Integer speedMs;
}
