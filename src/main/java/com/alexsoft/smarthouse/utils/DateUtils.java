package com.alexsoft.smarthouse.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class DateUtils {

    @Value("#{T(java.time.ZoneId).of('${time.local-time-zone}')}")
    private final ZoneId userTimezone;

    @Value("#{T(java.time.format.DateTimeFormatter).ofPattern('${time.chart-date-time-pattern}')}")
    private final DateTimeFormatter chartDateTimePattern;

    public static LocalDateTime roundDateTime(LocalDateTime localDateTime, int round, TemporalUnit temporalUnit) {  // TODO remove static, fix the tests
        if (round == 0) {
            return localDateTime;
        }
        LocalDateTime roundedLocalDateTime = localDateTime.withSecond(0).withNano(0);
        if (temporalUnit.equals(ChronoUnit.MINUTES)) {
            return roundedLocalDateTime.withMinute(localDateTime.getMinute() / round * round);
        } else if (temporalUnit.equals(ChronoUnit.HOURS)) {
            return roundedLocalDateTime.withHour(localDateTime.getHour() / round * round).withMinute(0);
        } else if (temporalUnit.equals(ChronoUnit.DAYS)) {
            return roundedLocalDateTime.withDayOfMonth(localDateTime.getDayOfMonth() / round * round).withHour(0).withMinute(0);
        } else if (temporalUnit.equals(ChronoUnit.MONTHS)) {
            return roundedLocalDateTime.withMonth(localDateTime.getMonthValue()).withDayOfMonth(1).withHour(0).withMinute(0);
        }
        return roundedLocalDateTime;
    }

    public LocalDateTime getInterval(Integer minutes, Integer hours, Integer days, boolean utc) {
        return ZonedDateTime.now(utc ? ZoneId.of("UTC") : userTimezone).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours));
    }

    public LocalDateTime toLocalDateTime(LocalDateTime ts) {
        return ZonedDateTime.of(ts, ZoneId.of("UTC")).withZoneSameInstant(userTimezone).toLocalDateTime();
    }

    public LocalDateTime getLocalDateTime() {
        return ZonedDateTime.now(userTimezone).toLocalDateTime();
    }

    public LocalDateTime getUtc() {
        return ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
    }

    public LocalDateTime toLocalDateTimeAtZone(LocalDateTime ts, Optional<String> timeZone) {
        if (timeZone.isEmpty()) {
            return toLocalDateTime(ts);
        }
        return ZonedDateTime.of(ts, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of(timeZone.get())).toLocalDateTime();
    }

    public LocalDateTime toUtc(LocalDateTime local) {
        if (local == null) {
            return null;
        }
        return ZonedDateTime.of(local, userTimezone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    public LocalDateTime timestampToLocalDateTime(Timestamp ts) {
        return ts.toLocalDateTime();
    }

    public String timestampToLocalDateTimeString(Timestamp ts) {
        return timestampToLocalDateTime(ts).format(chartDateTimePattern);
    }

    public String timestampToLocalDateTimeString(Timestamp ts, DateTimeFormatter dateTimeFormatter) {
        return timestampToLocalDateTime(ts).format(dateTimeFormatter);
    }

    public LocalDateTime toLocalDateTime(Calendar calendar) {
        return LocalDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId());
    }

    public Calendar toCalendar(LocalDateTime localDateTime) {   //  do not delete
        ZonedDateTime zonedDateTime = localDateTime.atZone(userTimezone);
        Date date = Date.from(zonedDateTime.toInstant());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public Timestamp convertToTimestamp(LocalDateTime localDateTime) {
        return Timestamp.valueOf(localDateTime);
    }

    public LocalDateTime convertToLocalDateTime(Timestamp timestamp) {
        return timestamp.toLocalDateTime();
    }


    public String localDateTimeToString(LocalDateTime localDateTime) {
        return localDateTime.format(chartDateTimePattern);
    }
}
