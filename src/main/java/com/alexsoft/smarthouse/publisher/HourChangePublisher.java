package com.alexsoft.smarthouse.publisher;

import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.repository.HourChangeTrackerRepository;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HourChangePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HourChangePublisher.class);


    private final ApplicationEventPublisher eventPublisher;
    private final DateUtils dateUtils;
    private final HourChangeTrackerRepository hourChangeTrackerRepository;

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        int currentHour = dateUtils.getLocalDateTime().getHour();
        int previousHour = hourChangeTrackerRepository.getPreviousHour();

        if (currentHour != previousHour) {
            LOGGER.info("New hour event: {}", currentHour);
            hourChangeTrackerRepository.updatePreviousHour(currentHour);
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
