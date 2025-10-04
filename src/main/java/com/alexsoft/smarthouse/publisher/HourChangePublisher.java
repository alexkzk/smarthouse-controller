package com.alexsoft.smarthouse.publisher;

import com.alexsoft.smarthouse.event.HourChangedEvent;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HourChangePublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final DateUtils dateUtils;
    private int previousHour = -1;

    @Scheduled(fixedRate = 1000)
    public void detectHourChange() {
        int currentHour = dateUtils.getLocalDateTime().getHour();
        if (currentHour != previousHour) {
            previousHour = currentHour;
            HourChangedEvent event = new HourChangedEvent(this, currentHour);
            eventPublisher.publishEvent(event);
        }
    }
}
