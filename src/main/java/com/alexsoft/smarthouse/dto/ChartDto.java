package com.alexsoft.smarthouse.dto;

import lombok.Data;

@Data
public class ChartDto {
    private Object[] aqi;
    private Object[] indoorTemps;
    private Object[] outdoorTemps;
    private Object[] ahs;
    private Object[] rhs;
    private Object[] aqiColors;
    private Object[] outdoorColors;
    private Object[] indoorColors;
    private Object[] ahsColors;
    private Object[] rhsColors;
}
