package com.alexsoft.smarthouse.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class DateUtils {

    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId MQTT_PRODUCER_TIMEZONE_ID = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter HOURLY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("E d, HH:mm");

    @Value("#{T(java.time.ZoneId).of('${time.local-time-zone}')}")
    private final ZoneId userTimezone;

    @Value("#{T(java.time.format.DateTimeFormatter).ofPattern('${time.chart-date-time-pattern}')}")
    private final DateTimeFormatter chartDateTimePattern;

    public LocalDateTime roundDateTime(LocalDateTime localDateTime, int round, TemporalUnit temporalUnit) {
        if (round == 0) {
            return localDateTime;
        }
        LocalDateTime roundedLocalDateTime = localDateTime.withSecond(0).withNano(0);
        if (temporalUnit.equals(ChronoUnit.MINUTES)) {
            return roundedLocalDateTime.withMinute(localDateTime.getMinute() / round * round);
        } else if(temporalUnit.equals(ChronoUnit.HOURS)) {
            return roundedLocalDateTime.withHour(localDateTime.getHour() / round * round).withMinute(0);
        } else if(temporalUnit.equals(ChronoUnit.DAYS)) {
            return roundedLocalDateTime.withHour(localDateTime.getDayOfMonth() / round * round).withHour(0).withMinute(0);
        }
        return roundedLocalDateTime;
    }

    public LocalDateTime getInterval(Integer minutes, Integer hours, Integer days, boolean utc) {
        return ZonedDateTime.now(utc ? ZoneId.of("UTC") : MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
            .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
            .minus(Duration.ofDays(days == null || days < 0 ? 0 : days))
            .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours));
    }

    public LocalDateTime timestampToLocalDateTime(Timestamp ts) {
        return ZonedDateTime.of(ts.toLocalDateTime(), UTC).withZoneSameInstant(userTimezone).toLocalDateTime();
    }

    public String timestampToLocalDateTimeString(Timestamp ts) {
        return timestampToLocalDateTime(ts).format(chartDateTimePattern);
    }

    public String localDateTimeToString(LocalDateTime localDateTime) {
        return localDateTime.format(chartDateTimePattern);
    }
}
