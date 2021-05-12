package com.alexsoft.smarthouse.dto;

import lombok.Data;

@Data
public class ChartDto {
    private Object[] aqi;
    private Object[] indoorTemps;
    private Object[] outdoorTemps;
    private Object[] ahs;
    private Object[] rhs;
}
