package com.alexsoft.smarthouse.environment;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired when sunset occurs.
 */
public class SunsetEvent extends ApplicationEvent {

    /**
     * Constructs a new SunsetEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public SunsetEvent(Object source) {
        super(source);
    }
}
