package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.alexsoft.smarthouse.utils.DateUtils.getUtc;

@Service
@RequiredArgsConstructor
public class MessageSenderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSenderService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EventRepository eventRepository;

    @Value("${mqtt.msgSendingEnabled}")
    private boolean msgSendingEnabled;

    private final IntegrationFlow mqttOutboundFlow;

    public void sendMessage(String topic, String messagePayload) {
        try {
            eventRepository.save(Event.builder().utcTime(getUtc()).data(OBJECT_MAPPER.readValue(messagePayload, Map.class)).type("outbound.mqtt.msg").build());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to log outbound MQTT message payload {}", messagePayload);
        }
        if (!msgSendingEnabled) {
            LOGGER.info("mqtt.msg.send.skipped: topic={}, payload={}", topic, messagePayload);
            return;
        }
        LOGGER.info("mqtt.msg.send: topic={}, payload={}", topic, messagePayload);
        mqttOutboundFlow.getInputChannel().send(MessageBuilder.withPayload(messagePayload).setHeader("mqtt_topic", topic).build());
    }

}
