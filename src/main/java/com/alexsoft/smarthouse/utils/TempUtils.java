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

    public static Double calculateRelativeHumidityV2(Double temp, Double ah) {
        if (temp == null || ah == null || ah < 0 || temp < -273.15) { // temp cannot be below absolute zero
            return null;
        }

        // Calculate the denominator
        double denominator = 6.112 * Math.pow(2.71828, (17.67 * temp) / (243.5 + temp)) * 2.1674;
        if (denominator == 0) {
            return null; // Avoid division by zero
        }

        // Calculate the relative humidity
        double rh = (ah * (273.15 + temp)) / denominator;

        // Ensure rh is within bounds (0 to 100%)
        if (rh < 0 || rh > 100) {
            return null;
        }

        // Round to one decimal place and return as Double
        return Math.round(rh * 10) / 10.0;
    }

}
