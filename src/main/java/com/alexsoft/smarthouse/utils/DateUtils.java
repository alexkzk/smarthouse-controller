package com.alexsoft.smarthouse.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_PRODUCER_TIMEZONE_ID;

public class DateUtils {

    public static LocalDateTime roundDateTime(LocalDateTime localDateTime, int roundMinutes) {
        if (roundMinutes == 0) {
            return localDateTime;
        }
        return localDateTime.withSecond(0).withNano(0)
                .withMinute(localDateTime.getMinute() / roundMinutes * roundMinutes);
    }

    public static LocalDateTime getInterval(Integer minutes, Integer hours, Integer days) {

        return ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
            .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
            .minus(Duration.ofDays(days == null || days < 0 ? 0 : days))
            .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours));
    }
}
