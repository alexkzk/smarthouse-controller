package com.alexsoft.smarthouse.mqtt;

import com.alexsoft.smarthouse.core.Event;
import com.alexsoft.smarthouse.core.EventRepository;
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

import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;

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
            logOutboundEvent(topic, messagePayload);
        } catch (Exception e) {
            LOGGER.error("Unexpected error logging outbound MQTT message: topic={}, payload={}", topic, messagePayload, e);
        }
        if (!msgSendingEnabled) {
            LOGGER.info("mqtt.msg.send.skipped: topic={}, payload={}", topic, messagePayload);
            return;
        }
        LOGGER.info("mqtt.msg.send: topic={}, payload={}", topic, messagePayload);
        mqttOutboundFlow.getInputChannel().send(MessageBuilder.withPayload(messagePayload).setHeader("mqtt_topic", topic).build());
    }

    private void logOutboundEvent(String topic, String messagePayload) {
        try {
            Map<String, Object> data = parsePayload(messagePayload);
            String resolvedTopic = determineTopic(topic);
            String device = determineDevice(resolvedTopic, data);

            eventRepository.save(Event.builder()
                    .utcTime(getUtc())
                    .data(data)
                    .type("outbound.mqtt.msg")
                    .mqttTopic(resolvedTopic)
                    .device(device)
                    .build());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to log outbound MQTT message payload {}", messagePayload);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String messagePayload) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(messagePayload, Map.class);
    }

    private String determineDevice(String topic, Map<String, Object> data) {
        if (data != null && data.containsKey("device")) {
            Object devObj = data.get("device");
            if (devObj != null) {
                return devObj.toString();
            }
        }
        if (topic != null && topic.startsWith("zigbee2mqtt/") && !topic.startsWith("zigbee2mqtt/bridge")) {
            String[] parts = topic.split("/");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return null;
    }

    private String determineTopic(String topic) {
        return topic;
    }

}
