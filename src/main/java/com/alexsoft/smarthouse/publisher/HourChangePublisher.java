package com.alexsoft.smarthouse.publisher;

import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.event.SunsetEvent;
import com.alexsoft.smarthouse.repository.HourChangeTrackerRepository;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;

import static com.alexsoft.smarthouse.utils.Constants.APPLICATION_OPERATION_TIMEZONE;

@Component
@RequiredArgsConstructor
@Slf4j
public class HourChangePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HourChangePublisher.class);
    public static final Location USER_LOCATION = new Location("25.76", "-80.19");

    private final ApplicationEventPublisher eventPublisher;
    private final DateUtils dateUtils;
    private final HourChangeTrackerRepository hourChangeTrackerRepository;

    private int previousHour;
    private int previousMinute = -1;

    @EventListener(ApplicationReadyEvent.class)
    public void readLastHour() {
        previousHour = hourChangeTrackerRepository.getPreviousHour();
    }

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        LocalDateTime localDateTime = dateUtils.getLocalDateTime();
        int currentHour = localDateTime.getHour();
        int currentMinute = localDateTime.getMinute();

        if (previousMinute != currentMinute) {  //  check astro events
            previousMinute = currentMinute;
            SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
            LocalDateTime sunsetDateTime = dateUtils.toLocalDateTime(sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance()));
            Object lastSunsetEvent = hourChangeTrackerRepository.getLastSunsetEvent();
            LocalDateTime lastSunsetReported = lastSunsetEvent == null ? null : dateUtils.convertToLocalDateTime((Timestamp) lastSunsetEvent);
            if (localDateTime.isAfter(sunsetDateTime) && (lastSunsetReported == null || !lastSunsetReported.toLocalDate().equals(LocalDate.now()))) {
                LOGGER.info("Sunset event");
                eventPublisher.publishEvent(new SunsetEvent(this));
                hourChangeTrackerRepository.updateLastSunsetEvent(dateUtils.convertToTimestamp(dateUtils.toUtc(localDateTime)));
            }

        }

        if (currentHour != previousHour) {  //  check new hour event
            LOGGER.info("New hour event: {}", currentHour);
            hourChangeTrackerRepository.updatePreviousHour(currentHour);
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
