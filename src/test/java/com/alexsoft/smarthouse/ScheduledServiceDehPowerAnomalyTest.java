package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.environment.IndicationV3;
import com.alexsoft.smarthouse.environment.IndicationRepositoryV3;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.environment.IndicationServiceV3;
import com.alexsoft.smarthouse.mqtt.MessageSenderService;
import com.alexsoft.smarthouse.watchdog.internal.ScheduledService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.alexsoft.smarthouse.appliance.ApplianceState.OFF;
import static com.alexsoft.smarthouse.appliance.ApplianceState.ON;
import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledServiceDehPowerAnomalyTest {

    @Mock ApplianceService applianceService;
    @Mock ApplianceFacade applianceFacade;
    @Mock IndicationRepositoryV3 indicationRepositoryV3;
    @Mock IndicationServiceV3 indicationServiceV3;
    @Mock MessageSenderService messageSenderService;

    @InjectMocks ScheduledService scheduledService;

    private static final String BLINK_PAYLOAD = "{\"effect\": \"blink\"}";

    private Appliance deh(ApplianceState state, LocalDateTime switchedOn) {
        Appliance a = new Appliance();
        a.setCode("DEH");
        a.setState(state, switchedOn);
        return a;
    }

    private Appliance dehOnLongRunning() {
        return deh(ON, getUtc().minusHours(1));
    }

    private IndicationV3 powerReading(double watts) {
        return IndicationV3.builder()
                .locationId("DEH")
                .measurementType("power")
                .value(watts)
                .utcTime(LocalDateTime.now())
                .localTime(LocalDateTime.now())
                .build();
    }

    @Test
    void dehOff_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(deh(OFF, getUtc().minusHours(1))));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
        verifyNoInteractions(indicationRepositoryV3);
    }

    @Test
    void dehNotFound_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.empty());

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
        verifyNoInteractions(indicationRepositoryV3);
    }

    @Test
    void dehOn_switchedOnNull_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(deh(ON, null)));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
        verifyNoInteractions(indicationRepositoryV3);
    }

    @Test
    void dehOn_withinStartupGracePeriod_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(deh(ON, getUtc().minusMinutes(3))));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
        verifyNoInteractions(indicationRepositoryV3);
    }

    @Test
    void dehOn_noLastReading_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(dehOnLongRunning()));
        when(indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power"))
                .thenReturn(Optional.empty());

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void dehOn_lastReadingZeroWatts_alertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(dehOnLongRunning()));
        when(indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power"))
                .thenReturn(Optional.of(powerReading(0.0)));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService).sendMessage(eq("zigbee2mqtt/LED_OVER_TV/set"), eq(BLINK_PAYLOAD));
        verify(messageSenderService).sendMessage(eq("zigbee2mqtt/LED_UNDER_TV/set"), eq(BLINK_PAYLOAD));
        verify(messageSenderService).sendMessage(eq("zigbee2mqtt/LED_OVER_BED/set"), eq(BLINK_PAYLOAD));
    }

    @Test
    void dehOn_lastReadingBelowThreshold_alertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(dehOnLongRunning()));
        when(indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power"))
                .thenReturn(Optional.of(powerReading(49.9)));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, times(3)).sendMessage(anyString(), eq(BLINK_PAYLOAD));
    }

    @Test
    void dehOn_lastReadingAtThreshold_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(dehOnLongRunning()));
        when(indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power"))
                .thenReturn(Optional.of(powerReading(50.0)));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void dehOn_lastReadingAtOperatingPower_noAlertSent() {
        when(applianceService.getApplianceByCode("DEH")).thenReturn(Optional.of(dehOnLongRunning()));
        when(indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power"))
                .thenReturn(Optional.of(powerReading(600.0)));

        scheduledService.checkDehPowerAnomaly();

        verify(messageSenderService, never()).sendMessage(anyString(), anyString());
    }
}
