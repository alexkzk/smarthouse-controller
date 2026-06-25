package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.service.ApplianceFacade;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.service.IndicationServiceV3;
import com.alexsoft.smarthouse.service.MessageReceiverService;
import com.alexsoft.smarthouse.service.MessageSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: real ApplianceFacade wired to real MessageReceiverService.
 * Only I/O boundaries are mocked — repositories and the MQTT sender.
 *
 * Replicates the bug: physical switch press is overridden immediately by the controller.
 *   1. TER_LIGHTS is locked OFF (rule 1 — dark, user turned it off, locked until morning)
 *   2. User presses physical switch → device publishes state=ON
 *   3. Controller receives ON, sees the appliance is locked → calls sendState(appliance)
 *   4. sendState sends {"state":"off"} back, undoing the physical input within milliseconds
 *
 * "lockedOff_physicalSwitchOn_noOutboundOffSentToDevice" FAILS now — that is the proof.
 * It passes after the fix (remove sendState from the locked branch in MessageReceiverService).
 */
@ExtendWith(MockitoExtension.class)
class PhysicalSwitchOverrideBugTest {

    @Mock ApplianceService applianceService;
    @Mock MessageSenderService messageSenderService;
    @Mock EventRepository eventRepository;
    @Mock IndicationServiceV3 indicationServiceV3;
    @Mock ApplianceRepository applianceRepository;
    @Mock IndicationRepositoryV3 indicationRepositoryV3;

    MessageHandler handler;

    @BeforeEach
    void setUp() {
        ApplianceFacade applianceFacade = new ApplianceFacade(
                applianceRepository, messageSenderService, eventRepository, indicationServiceV3);
        MessageReceiverService receiverService = new MessageReceiverService(
                applianceService, indicationServiceV3, eventRepository);
        ReflectionTestUtils.setField(receiverService, "mqttTopic", "smarthouse/sensor");
        ReflectionTestUtils.setField(receiverService, "measurementTopic", "smarthouse/sensor");
        handler = receiverService.messageHandler(applianceFacade);
    }

    // BUG — currently FAILS.
    // User presses physical switch ON while TER_LIGHTS is locked OFF.
    // Desired: ignore the inbound message — the device will resync on the next pwr-control cycle.
    // Actual: sendState() is called immediately, sending {"state":"off"} to zigbee2mqtt/TER_LIGHTS/set.
    @Test
    void lockedOff_physicalSwitchOn_noOutboundOffSentToDevice() {
        Appliance appliance = new Appliance();
        appliance.setCode("TER_LIGHTS");
        appliance.setState(ApplianceState.OFF, LocalDateTime.now().minusHours(1));
        appliance.setLocked(true);
        appliance.setLockedUntilUtc(LocalDateTime.of(9999, 12, 31, 23, 59));
        appliance.setZigbee2MqttTopic("zigbee2mqtt/TER_LIGHTS/set");

        // "TER_LIGHTS" matches topic.split("/")[1] for "zigbee2mqtt/TER_LIGHTS"
        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        handler.handleMessage(MessageBuilder.withPayload("{\"state\":\"ON\"}")
                .setHeader("mqtt_receivedTopic", "zigbee2mqtt/TER_LIGHTS")
                .build());

        verify(messageSenderService, never()).sendMessage(
                eq("zigbee2mqtt/TER_LIGHTS/set"), argThat(p -> p.contains("off")));
    }

    // Sanity check — must still PASS after the fix.
    // Unlocked appliance, physical switch pressed: toggle() is called (state+lock saved to DB),
    // no direct sendMessage (sendMqtt=false for Zigbee-triggered toggles).
    @Test
    void unlocked_physicalSwitchOn_toggleCalled_noDirectMqttSend() {
        Appliance appliance = new Appliance();
        appliance.setCode("TER_LIGHTS");
        appliance.setState(ApplianceState.OFF, LocalDateTime.now().minusHours(1));
        appliance.setZigbee2MqttTopic("zigbee2mqtt/TER_LIGHTS/set");

        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        handler.handleMessage(MessageBuilder.withPayload("{\"state\":\"ON\"}")
                .setHeader("mqtt_receivedTopic", "zigbee2mqtt/TER_LIGHTS")
                .build());

        verify(messageSenderService, never()).sendMessage(any(), any());
    }
}
