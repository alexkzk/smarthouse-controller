package com.alexsoft.smarthouse.publisher;

import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.event.SunsetEvent;
import com.alexsoft.smarthouse.repository.HourChangeTrackerRepository;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.SunUtils;
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
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class HourChangePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HourChangePublisher.class);
    public static final Location USER_LOCATION = new Location("25.76", "-80.19");

    private final ApplicationEventPublisher eventPublisher;
    private final DateUtils dateUtils;
    private final HourChangeTrackerRepository hourChangeTrackerRepository;
    private final SunUtils sunUtils;

    private Integer lastReportedNewHour;
    private LocalDateTime lastSunsetReported;
    private boolean appReady = false;

    @EventListener(ApplicationReadyEvent.class)
    public void readLastHour() {
        lastReportedNewHour = hourChangeTrackerRepository.getPreviousHour();
        Object lastSunsetEvent = hourChangeTrackerRepository.getLastSunsetEvent();
        lastSunsetReported = lastSunsetEvent == null ? null : dateUtils.convertToLocalDateTime((Timestamp) lastSunsetEvent);
        appReady = true;
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void detectSunset() {
        if (!appReady) {
            return;
        }
        LocalDateTime localDateTime = dateUtils.getLocalDateTime();
        LocalDateTime sunsetDateTime = sunUtils.getSunsetTime();
        if (localDateTime.isAfter(sunsetDateTime) && (lastSunsetReported == null || !lastSunsetReported.toLocalDate().equals(LocalDate.now()))) {
            LOGGER.info("Sunset event");
            eventPublisher.publishEvent(new SunsetEvent(this));
            lastSunsetReported = localDateTime;
            hourChangeTrackerRepository.updateLastSunsetEvent(dateUtils.convertToTimestamp(localDateTime), lastSunsetReported == null);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        if (!appReady) {
            return;
        }
        LocalDateTime localDateTime = dateUtils.getLocalDateTime();
        int currentHour = localDateTime.getHour();
        if (!Objects.equals(currentHour, lastReportedNewHour)) {
            LOGGER.info("New hour event: {}", currentHour);
            hourChangeTrackerRepository.updatePreviousHour(currentHour, dateUtils.convertToTimestamp(localDateTime));
            lastReportedNewHour = currentHour;
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
