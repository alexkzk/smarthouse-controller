package com.alexsoft.smarthouse.utils;

public class RoundingUtils {

    public static Float roundFloat(Float floatToRound, int accuracy) {
        return floatToRound == null || Float.isNaN(floatToRound) ? null : (float) Math.round(floatToRound * accuracy) / accuracy;
    }

}
