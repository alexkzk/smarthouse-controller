package com.alexsoft.smarthouse.utils;

import java.time.LocalDateTime;

public class DateUtils {

    public static LocalDateTime roundDateTime(LocalDateTime localDateTime, int roundMinutes) {
        if (roundMinutes == 0) {
            return localDateTime;
        }
        return localDateTime.withSecond(0).withNano(0)
                .withMinute(localDateTime.getMinute() / roundMinutes * roundMinutes);
    }
}
