package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.mqtt.internal.MessageReceiverService;
import com.alexsoft.smarthouse.mqtt.MqttMessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageReceiverServiceTest {

    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks MessageReceiverService service;

    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = service.messageHandler();
    }

    private Message<String> mqttMessage(String topic, String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mqtt_receivedTopic", topic)
                .build();
    }

    @Test
    void publishesEvent() {
        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS/set", "{\"state\":\"ON\"}"));
        verify(eventPublisher).publishEvent(any(MqttMessageReceivedEvent.class));
    }
}
