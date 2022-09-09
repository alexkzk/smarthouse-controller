package com.alexsoft.smarthouse.enums;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public enum AggregationPeriod {
    INSTANT, HOURLY, DAILY, MONTHLY, MINUTELY;

    public static AggregationPeriod of(TemporalUnit temporalUnit) {
        if (temporalUnit.equals(ChronoUnit.MINUTES)) {
            return MINUTELY;
        } else if (temporalUnit.equals(ChronoUnit.HOURS)) {
            return HOURLY;
        } else if(temporalUnit.equals(ChronoUnit.DAYS)) {
            return DAILY;
        } else if(temporalUnit.equals(ChronoUnit.MONTHS)) {
            return MONTHLY;
        } else {
            throw new RuntimeException("Unsupported temporal type " + temporalUnit);
        }
    }
}
