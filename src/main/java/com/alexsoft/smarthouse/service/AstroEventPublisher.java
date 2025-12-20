package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.event.SunsetEvent;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.repository.HourChangeTrackerRepository;
import com.luckycatlabs.sunrisesunset.dto.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static com.alexsoft.smarthouse.utils.DateUtils.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AstroEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstroEventPublisher.class);
    public static final Location USER_LOCATION = new Location("25.76", "-80.19");

    private final ApplicationEventPublisher eventPublisher;
    private final HourChangeTrackerRepository hourChangeTrackerRepository;
    private final EventRepository eventRepository;

    private Integer lastReportedNewHour;
    private LocalDateTime lastSunsetReported;
    private boolean appReady = false;

    @EventListener(ApplicationReadyEvent.class)
    public void readLastHour() {
        lastReportedNewHour = hourChangeTrackerRepository.getPreviousHour();
        Object lastSunsetEvent = hourChangeTrackerRepository.getLastSunsetEvent();
        lastSunsetReported = lastSunsetEvent == null ? null : convertToLocalDateTime((Timestamp) lastSunsetEvent);
        appReady = true;
        eventRepository.save(Event.builder().utcTime(toUtc(getLocalDateTime())).type("app-startup").build());
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent() {
        eventRepository.save(Event.builder().utcTime(toUtc(getLocalDateTime())).type("app-shutdown").build());
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void detectSunset() {
        if (!appReady) {
            return;
        }
        LocalDateTime localDateTime = getLocalDateTime();
        LocalDateTime sunsetDateTime = getSunsetTime();
        if (localDateTime.isAfter(sunsetDateTime) && (lastSunsetReported == null || !lastSunsetReported.toLocalDate().equals(LocalDate.now()))) {
            LOGGER.info("Sunset event");
            eventRepository.save(Event.builder().utcTime(toUtc(localDateTime)).type("sunset").build());
            eventPublisher.publishEvent(new SunsetEvent(this));
            hourChangeTrackerRepository.updateLastSunsetEvent(convertToTimestamp(localDateTime), lastSunsetReported == null);
            lastSunsetReported = localDateTime;
        }
    }

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        if (!appReady) {
            return;
        }
        LocalDateTime localDateTime = getLocalDateTime();
        int currentHour = localDateTime.getHour();
        if (!Objects.equals(currentHour, lastReportedNewHour)) {
            eventRepository.save(Event.builder().utcTime(toUtc(localDateTime)).type("new-hour").build());
            LOGGER.info("New hour event: {}", currentHour);
            hourChangeTrackerRepository.updatePreviousHour(currentHour, convertToTimestamp(localDateTime));
            lastReportedNewHour = currentHour;
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
