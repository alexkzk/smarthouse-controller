package com.alexsoft.smarthouse.utils;

public class MathUtils {

    public static final String STATUS_STRING_NULL_MEASURE = "-";

    public static Float round(Float floatToRound, int accuracy) {
        return floatToRound == null || Float.isNaN(floatToRound) ? null : (float) Math.round(floatToRound * accuracy) / accuracy;
    }

    public static Long round(Double d) {
        return roundDouble(d);
    }

    public static Long round(double d) {
        return roundDouble(d);
    }

    public static Integer doubleToInt(double d) {
        if (Double.isNaN(d)){
            return null;
        } else {
            long round = Math.round(d);
            if (round > Integer.MAX_VALUE) {
                throw new RuntimeException();
            }
            return (int) round;
        }
    }

    public static String getNumberOrString(Number number, String string) {
        return number == null ? string : String.valueOf(number);
    }

    public static String measureToString(Long measure) {
        if (measure == null) {
            return STATUS_STRING_NULL_MEASURE;
        } else {
            return String.valueOf(measure);
        }
    }

    public static boolean isNullOrNan(Double d) {
        return d == null || Double.isNaN(d);
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

    private static Long roundDouble(double d) {
        if (Double.isNaN(d)) {
            return null;
        } else {
            return Math.round(d);
        }
    }

}
