package com.alexsoft.smarthouse.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageService.class);

    @Value("${mqtt.msgSendingEnabled}")
    private boolean msgSendingEnabled;

    private final IntegrationFlow mqttOutboundFlow;

    public void sendMessage(String topic, String messagePayload) {
        if (!msgSendingEnabled) {
            return;
        }
        LOGGER.info("Sending MQTT message: topic={}, payload={}", topic, messagePayload);
        mqttOutboundFlow.getInputChannel().send(MessageBuilder.withPayload(messagePayload).setHeader("mqtt_topic", topic).build());
    }

}
