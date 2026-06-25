package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.environment.IndicationServiceV3;
import com.alexsoft.smarthouse.mqtt.MessageReceiverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageReceiverServiceTest {

    @Mock ApplianceService applianceService;
    @Mock IndicationServiceV3 indicationServiceV3;
    @Mock EventRepository eventRepository;
    @Mock ApplianceFacade applianceFacade;

    @InjectMocks MessageReceiverService service;

    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "mqttTopic", "custom/sensor-topic");
        ReflectionTestUtils.setField(service, "measurementTopic", "custom/sensor-topic");
        handler = service.messageHandler(applianceFacade);
    }

    private Message<String> mqttMessage(String topic, String payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mqtt_receivedTopic", topic)
                .build();
    }

    private Appliance applianceWithState(String code, ApplianceState state) {
        Appliance a = new Appliance();
        a.setCode(code);
        a.setState(state, LocalDateTime.now().minusHours(1));
        return a;
    }

    // /set topics are the app's own command channel — receiving them back is an echo or retained
    // message from the broker, not a real device state change. Toggle must never be triggered.
    @Test
    void setTopic_doesNotTriggerToggle() {
        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS/set", "{\"state\":\"ON\"}"));

        verify(applianceFacade, never()).toggle(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void setTopic_doesNotQueryAppliances() {
        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS/set", "{\"state\":\"ON\"}"));

        verify(applianceService, never()).getApplianceByCode(any());
        verify(applianceService, never()).getApplianceByZigbeeTopic(any());
    }

    @Test
    void stateTopic_triggersToggleOnStateChange() {
        Appliance appliance = applianceWithState("TER_LIGHTS", ApplianceState.OFF);
        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS", "{\"state\":\"ON\"}"));

        verify(applianceFacade).toggle(eq(appliance), eq(ApplianceState.ON), any(), eq("zigbee2mqtt/TER_LIGHTS"), eq(false));
    }

    @Test
    void stateTopic_noToggleWhenStateUnchanged() {
        Appliance appliance = applianceWithState("TER_LIGHTS", ApplianceState.ON);
        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS", "{\"state\":\"ON\"}"));

        verify(applianceFacade, never()).toggle(any(), any(), any(), any(), anyBoolean());
    }

    // BUG — currently FAILS.
    // Physical switch press must override the lock and save the new state to DB.
    // Desired: toggle() is called regardless of lock — state persisted, lock re-evaluated by setLock().
    // Actual: toggle() is skipped because MessageReceiverService checks !appliance.isLocked().
    @Test
    void lockedAppliance_physicalSwitchOn_toggleCalledOverridingLock() {
        Appliance appliance = applianceWithState("TER_LIGHTS", ApplianceState.OFF);
        appliance.setLocked(true);
        appliance.setLockedUntilUtc(java.time.LocalDateTime.of(9999, 12, 31, 23, 59));
        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        handler.handleMessage(mqttMessage("zigbee2mqtt/TER_LIGHTS", "{\"state\":\"ON\"}"));

        verify(applianceFacade).toggle(eq(appliance), eq(ApplianceState.ON), any(), eq("zigbee2mqtt/TER_LIGHTS"), eq(false));
    }

    @Test
    void bridgeTopic_doesNotTriggerToggle() {
        handler.handleMessage(mqttMessage("zigbee2mqtt/bridge/state", "{\"state\":\"online\"}"));

        verify(applianceFacade, never()).toggle(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void vibrationTrue_savesIndicationAs1() {
        when(applianceService.getApplianceByCode(anyString())).thenReturn(Optional.empty());
        when(applianceService.getApplianceByZigbeeTopic(anyString())).thenReturn(Optional.empty());

        handler.handleMessage(mqttMessage("zigbee2mqtt/0xa4c138b53c1c52c4", "{\"vibration\":true}"));

        verify(indicationServiceV3).saveAll(argThat(iterable -> {
            java.util.List<com.alexsoft.smarthouse.environment.IndicationV3> list = new java.util.ArrayList<>();
            iterable.forEach(list::add);
            return list.size() == 1
                && list.get(0).getMeasurementType().equals("vibration")
                && list.get(0).getValue() == 1.0
                && list.get(0).getLocationId().equals("0xa4c138b53c1c52c4");
        }));
    }

    @Test
    void vibrationFalse_savesIndicationAs0() {
        when(applianceService.getApplianceByCode(anyString())).thenReturn(Optional.empty());
        when(applianceService.getApplianceByZigbeeTopic(anyString())).thenReturn(Optional.empty());

        handler.handleMessage(mqttMessage("zigbee2mqtt/0xa4c138b53c1c52c4", "{\"vibration\":false}"));

        verify(indicationServiceV3).saveAll(argThat(iterable -> {
            java.util.List<com.alexsoft.smarthouse.environment.IndicationV3> list = new java.util.ArrayList<>();
            iterable.forEach(list::add);
            return list.size() == 1
                && list.get(0).getMeasurementType().equals("vibration")
                && list.get(0).getValue() == 0.0;
        }));
    }
}
