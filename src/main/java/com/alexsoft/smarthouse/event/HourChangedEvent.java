package com.alexsoft.smarthouse.event;

import org.springframework.context.ApplicationEvent;

public class HourChangedEvent extends ApplicationEvent {

    private final int hour;

    public HourChangedEvent(Object source, int hour) {
        super(source);
        this.hour = hour;
    }

    public int getHour() {
        return hour;
    }
}
