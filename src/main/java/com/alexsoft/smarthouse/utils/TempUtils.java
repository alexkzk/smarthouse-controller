package com.alexsoft.smarthouse.utils;

public class TempUtils {

    public Integer calculateRelativeHumidity(final Float temp, final Float devpoint) {
        if (temp == null || devpoint == null || devpoint > temp) {
            return null;
        }
        long rh = Math.round(100*(Math.exp((17.625*devpoint)/(243.04+devpoint))/Math.exp((17.625*temp)/(243.04+temp))));
        if (rh > Integer.MAX_VALUE) {
            return null;
        }
        return (int) rh;
    }

    public Float calculateAbsoluteHumidity(Float temp, Integer rh) {
        if (temp == null || rh == null) {
            return null;
        }
        double ah = 6.112 * Math.pow(2.71828, 17.67 * temp / (243.5 + temp)) * rh * 2.1674 / (273.15 + temp);
        return Math.round(ah * 10) / 10.0F;
    }
}
