package com.alexsoft.smarthouse.utils;

public class MathUtils {

    public static Float round(Float floatToRound, int accuracy) {
        return floatToRound == null || Float.isNaN(floatToRound) ? null : (float) Math.round(floatToRound * accuracy) / accuracy;
    }

    private Long round(Double d) {
        if (d.isNaN()) {
            return null;
        } else {
            return Math.round(d);
        }
    }

    private static Double min(Double a, Double b) {
        if (Double.isNaN(a) && !Double.isNaN(b)) {
            return b;
        } else if (!Double.isNaN(a) && Double.isNaN(b)) {
            return a;
        } else if (Double.isNaN(a) && Double.isNaN(b)) {
            return Double.NaN;
        } else {
            return Math.min(a, b);
        }
    }

    public static Long min(Long a, Long b) {
        if (a == null && b != null) {
            return b;
        } else if (a != null && b == null) {
            return a;
        } else if (a == null && b == null) {
            return null;
        } else {
            return Math.min(a, b);
        }
    }

}
