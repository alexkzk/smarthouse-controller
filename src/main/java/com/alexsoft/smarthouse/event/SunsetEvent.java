package com.alexsoft.smarthouse.event;

import org.springframework.context.ApplicationEvent;

public class SunsetEvent extends ApplicationEvent {

    public SunsetEvent(Object source) {
        super(source);
    }
}
