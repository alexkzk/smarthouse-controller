package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.appliance.listener.MqttApplianceListener;
import com.alexsoft.smarthouse.mqtt.MqttMessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhysicalSwitchOverrideBugTest {

    @Mock ApplianceService applianceService;
    @Mock ApplianceFacade applianceFacade;

    @InjectMocks MqttApplianceListener listener;

    private Appliance applianceWithState(String code, ApplianceState state) {
        Appliance a = new Appliance();
        a.setCode(code);
        a.setState(state, LocalDateTime.now().minusHours(1));
        return a;
    }

    @Test
    void lockedAppliance_physicalSwitchOn_toggleCalledOverridingLock() {
        Appliance appliance = applianceWithState("TER_LIGHTS", ApplianceState.OFF);
        appliance.setLocked(true);
        appliance.setLockedUntilUtc(java.time.LocalDateTime.of(9999, 12, 31, 23, 59));
        when(applianceService.getApplianceByCode("TER_LIGHTS")).thenReturn(Optional.of(appliance));

        listener.handleMqttMessage(new MqttMessageReceivedEvent(this, "zigbee2mqtt/TER_LIGHTS", "{\"state\":\"ON\"}"));

        verify(applianceFacade).toggle(eq(appliance), eq(ApplianceState.ON), any(), eq("zigbee2mqtt/TER_LIGHTS"), eq(false));
    }
}
