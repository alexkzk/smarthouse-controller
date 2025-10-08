package com.alexsoft.smarthouse.utils;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Calendar;

import static com.alexsoft.smarthouse.publisher.HourChangePublisher.USER_LOCATION;
import static com.alexsoft.smarthouse.utils.Constants.APPLICATION_OPERATION_TIMEZONE;

@Service
@RequiredArgsConstructor
public class SunUtils {

    private final DateUtils dateUtils;

    public LocalDateTime getSunsetTime() {
        SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
        return dateUtils.toLocalDateTime(sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance()));
    }

    public LocalDateTime getSunriseTime() {
        SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
        return dateUtils.toLocalDateTime(sunriseSunsetCalculator.getOfficialSunriseCalendarForDate(Calendar.getInstance()));
    }

}
