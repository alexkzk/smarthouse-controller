package com.alexsoft.smarthouse.mqtt;

import org.springframework.context.ApplicationEvent;

public class MqttMessageReceivedEvent extends ApplicationEvent {
    private final String topic;
    private final String payload;

    public MqttMessageReceivedEvent(Object source, String topic, String payload) {
        super(source);
        this.topic = topic;
        this.payload = payload;
    }

    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
}
