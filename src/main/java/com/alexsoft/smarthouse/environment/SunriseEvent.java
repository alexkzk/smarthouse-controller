package com.alexsoft.smarthouse.environment;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired when sunrise occurs.
 */
public class SunriseEvent extends ApplicationEvent {

    /**
     * Constructs a new SunriseEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public SunriseEvent(Object source) {
        super(source);
    }
}
