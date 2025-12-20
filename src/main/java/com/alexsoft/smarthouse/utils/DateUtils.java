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

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.publisher.AstroEventPublisher.USER_LOCATION;
import static com.alexsoft.smarthouse.utils.Constants.APPLICATION_OPERATION_TIMEZONE;


public class DateUtils {

    private static final ZoneId userTimezone = ZoneId.of("America/New_York");

    private static final DateTimeFormatter chartDateTimePattern = DateTimeFormatter.ofPattern("E d, HH:mm");

    public static LocalDateTime roundDateTime(LocalDateTime localDateTime, int round, TemporalUnit temporalUnit) {
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

    public static LocalDateTime getInterval(Integer minutes, Integer hours, Integer days, boolean utc) {
        return ZonedDateTime.now(utc ? ZoneId.of("UTC") : userTimezone).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours));
    }

    public static LocalDateTime toLocalDateTime(LocalDateTime ts) {
        return ZonedDateTime.of(ts, ZoneId.of("UTC")).withZoneSameInstant(userTimezone).toLocalDateTime();
    }

    public static LocalDateTime getLocalDateTime() {
        return ZonedDateTime.now(userTimezone).toLocalDateTime();
    }

    public static LocalDateTime getUtc() {
        return ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
    }

    public static LocalDateTime toLocalDateTimeAtZone(LocalDateTime ts, Optional<String> timeZone) {
        if (timeZone.isEmpty()) {
            return toLocalDateTime(ts);
        }
        return ZonedDateTime.of(ts, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of(timeZone.get())).toLocalDateTime();
    }

    public static LocalDateTime toUtc(LocalDateTime local) {
        if (local == null) {
            return null;
        }
        return ZonedDateTime.of(local, userTimezone).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    public static LocalDateTime timestampToLocalDateTime(Timestamp ts) {
        return ts.toLocalDateTime();
    }

    public static String timestampToLocalDateTimeString(Timestamp ts) {
        return timestampToLocalDateTime(ts).format(chartDateTimePattern);
    }

    public static String timestampToLocalDateTimeString(Timestamp ts, DateTimeFormatter dateTimeFormatter) {
        return timestampToLocalDateTime(ts).format(dateTimeFormatter);
    }

    public static LocalDateTime toLocalDateTime(Calendar calendar) {
        return LocalDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId());
    }

    public static Calendar toCalendar(LocalDateTime localDateTime) {   //  do not delete
        ZonedDateTime zonedDateTime = localDateTime.atZone(userTimezone);
        Date date = Date.from(zonedDateTime.toInstant());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public static Timestamp convertToTimestamp(LocalDateTime localDateTime) {
        return Timestamp.valueOf(localDateTime);
    }

    public static LocalDateTime convertToLocalDateTime(Timestamp timestamp) {
        return timestamp.toLocalDateTime();
    }


    public static String localDateTimeToString(LocalDateTime localDateTime) {
        return localDateTime.format(chartDateTimePattern);
    }

    public static LocalDateTime sixThirtyAm() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));

        // Set target time to 6:30 AM
        ZonedDateTime sixThirtyAm = now.withHour(6).withMinute(30).withSecond(0).withNano(0);
        if (now.getHour() > 6 || (now.getHour() == 6 && now.getMinute() >= 30)) {
            // If current time is past 6:30 AM, move to the next day
            sixThirtyAm = sixThirtyAm.plusDays(1);
        }

        // Convert to UTC
        ZonedDateTime next6_30AMUtc = sixThirtyAm.withZoneSameInstant(ZoneId.of("UTC"));

        return next6_30AMUtc.toLocalDateTime();

    }

    public static LocalDateTime getSunsetTime() {
        SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
        return toLocalDateTime(sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance()));
    }

    public static LocalDateTime getSunriseTime() {
        SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
        LocalDateTime sunriseTime = toLocalDateTime(sunriseSunsetCalculator.getOfficialSunriseCalendarForDate(Calendar.getInstance()));
        if (getLocalDateTime().isAfter(sunriseTime)) {
            sunriseTime = sunriseTime.plusDays(1);
        }
        return sunriseTime;
    }
}
