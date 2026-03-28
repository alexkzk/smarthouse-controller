package com.alexsoft.smarthouse.event;

import org.springframework.context.ApplicationEvent;

public class SunriseEvent extends ApplicationEvent {

    public SunriseEvent(Object source) {
        super(source);
    }
}
