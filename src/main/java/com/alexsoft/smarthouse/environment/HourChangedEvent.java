package com.alexsoft.smarthouse.environment;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired when the hour of the day changes.
 */
public class HourChangedEvent extends ApplicationEvent {

    private final int hour;

    /**
     * Constructs a new HourChangedEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     * @param hour   the new hour of the day (0-23)
     */
    public HourChangedEvent(Object source, int hour) {
        super(source);
        this.hour = hour;
    }

    /**
     * Gets the hour of the day.
     *
     * @return the hour
     */
    public int getHour() {
        return hour;
    }
}
