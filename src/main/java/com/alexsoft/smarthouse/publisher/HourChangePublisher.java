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

    private int lastReportedNewHour;
    private LocalDateTime lastSunsetReported;

    @EventListener(ApplicationReadyEvent.class)
    public void readLastHour() {
        lastReportedNewHour = hourChangeTrackerRepository.getPreviousHour();
        Object lastSunsetEvent = hourChangeTrackerRepository.getLastSunsetEvent();
        lastSunsetReported = lastSunsetEvent == null ? null : dateUtils.convertToLocalDateTime((Timestamp) lastSunsetEvent);
        eventPublisher.publishEvent(new SunsetEvent(this));
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void checkSunsetSunrise() {
        LocalDateTime localDateTime = dateUtils.getLocalDateTime();
        SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(USER_LOCATION, APPLICATION_OPERATION_TIMEZONE);
        LocalDateTime sunsetDateTime = dateUtils.toLocalDateTime(sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance()));
        if (localDateTime.isAfter(sunsetDateTime) && (lastSunsetReported == null || !lastSunsetReported.toLocalDate().equals(LocalDate.now()))) {
            LOGGER.info("Sunset event");
            eventPublisher.publishEvent(new SunsetEvent(this));
            lastSunsetReported = localDateTime;
            hourChangeTrackerRepository.updateLastSunsetEvent(dateUtils.convertToTimestamp(dateUtils.toUtc(localDateTime)), lastSunsetReported == null);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        LocalDateTime localDateTime = dateUtils.getLocalDateTime();
        int currentHour = localDateTime.getHour();
        if (currentHour != lastReportedNewHour) {
            LOGGER.info("New hour event: {}", currentHour);
            hourChangeTrackerRepository.updatePreviousHour(currentHour);
            lastReportedNewHour = currentHour;
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
