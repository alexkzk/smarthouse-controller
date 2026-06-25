package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.core.Event;
import com.alexsoft.smarthouse.core.EventRepository;
import com.alexsoft.smarthouse.mqtt.MessageSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageSenderServiceTest {

    @Mock EventRepository eventRepository;
    @Mock IntegrationFlow mqttOutboundFlow;
    @Mock MessageChannel messageChannel;

    @InjectMocks MessageSenderService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "msgSendingEnabled", true);
        // Mock mqttOutboundFlow.getInputChannel() to return messageChannel so sendMessage doesn't NPE when enabled
        lenient().when(mqttOutboundFlow.getInputChannel()).thenReturn(messageChannel);
    }

    @Test
    void testSendMessagePowerControl() {
        String topic = "mqtt.smarthouse.power.control";
        String payload = "{\"device\":\"AC\",\"state\":\"on\"}";

        service.sendMessage(topic, payload);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent);
        assertEquals("outbound.mqtt.msg", savedEvent.getType());
        assertEquals(topic, savedEvent.getMqttTopic());
        assertEquals("AC", savedEvent.getDevice());
        assertEquals(Map.of("device", "AC", "state", "on"), savedEvent.getData());
    }

    @Test
    void testSendMessageZigbee() {
        String topic = "zigbee2mqtt/LED_OVER_TV/set";
        String payload = "{\"state\":\"on\",\"brightness\":160}";

        service.sendMessage(topic, payload);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent);
        assertEquals("outbound.mqtt.msg", savedEvent.getType());
        assertEquals(topic, savedEvent.getMqttTopic());
        assertEquals("LED_OVER_TV", savedEvent.getDevice());
        assertEquals(Map.of("state", "on", "brightness", 160), savedEvent.getData());
    }

    @Test
    void testSendMessageZigbeeBridge() {
        String topic = "zigbee2mqtt/bridge/request/device/remove";
        String payload = "{\"id\":\"0x123456\"}";

        service.sendMessage(topic, payload);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent);
        assertEquals("outbound.mqtt.msg", savedEvent.getType());
        assertEquals(topic, savedEvent.getMqttTopic());
        assertNull(savedEvent.getDevice());
        assertEquals(Map.of("id", "0x123456"), savedEvent.getData());
    }
}
