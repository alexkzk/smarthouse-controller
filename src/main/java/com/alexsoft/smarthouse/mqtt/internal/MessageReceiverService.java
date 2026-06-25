package com.alexsoft.smarthouse.mqtt.internal;
import com.alexsoft.smarthouse.mqtt.MqttMessageReceivedEvent;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageReceiverService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReceiverService.class);

    private final ApplicationEventPublisher eventPublisher;

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler messageHandler() {
        return message -> {
            String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            String payload = (String) message.getPayload();
            LOGGER.info("mqtt.msg.received: {}", payload);
            
            try {
                eventPublisher.publishEvent(new MqttMessageReceivedEvent(this, topic, payload));
            } catch (Exception e) {
                LOGGER.error("Error publishing MQTT event", e);
            }
        };
    }
}
