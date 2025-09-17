package com.alexsoft.smarthouse.utils;

public class TempUtils {

    public Integer calculateRelativeHumidity(final Float temp, final Float devpoint) {
        if (temp == null || devpoint == null || devpoint > temp) {
            return null;
        }
        long rh = Math.round(100 * (Math.exp((17.625 * devpoint) / (243.04 + devpoint)) / Math.exp((17.625 * temp) / (243.04 + temp))));
        if (rh > Integer.MAX_VALUE) {
            return null;
        }
        return (int) rh;
    }

    public Float calculateAbsoluteHumidity(Float temp, Integer rh) {
        return calculateAbsoluteHumidity(temp, rh, null);
    }

    public Float calculateAbsoluteHumidity(Float temp, Integer rh, Float pressure) {
        if (temp == null || rh == null) {
            return null; // Early return if temperature or relative humidity is null
        }

        // Default pressure to standard atmospheric pressure (760 mmHg) if null
        float defaultPressure = 760.0F; // Standard pressure in mmHg
        float actualPressure = (pressure != null) ? pressure : defaultPressure;

        // Convert pressure from mmHg to hPa: 1 mmHg = 1.33322 hPa
        double pressureHpa = actualPressure * 1.33322;

        // Absolute Humidity calculation
        double ah = 6.112 * Math.pow(2.71828, 17.67 * temp / (243.5 + temp)) * rh * 2.1674 / (273.15 + temp);

        // Adjust for actual pressure
        ah = ah * pressureHpa / 1013.25; // Scale based on actual pressure

        // Round the result to 2 decimal
        return Math.round(ah * 100) / 100.0F;
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
