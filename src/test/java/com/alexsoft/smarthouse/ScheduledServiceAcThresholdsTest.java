package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.environment.IndicationV3;
import com.alexsoft.smarthouse.environment.IndicationRepositoryV3;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.environment.IndicationServiceV3;
import com.alexsoft.smarthouse.watchdog.internal.ScheduledService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledServiceAcThresholdsTest {

    @Mock ApplianceService applianceService;
    @Mock IndicationRepositoryV3 indicationRepositoryV3;
    @Mock IndicationServiceV3 indicationServiceV3;

    @InjectMocks ScheduledService scheduledService;

    private Appliance ac(double setting, double hysteresisOn, double hysteresisOff) {
        Appliance a = new Appliance();
        a.setCode("AC");
        a.setSetting(setting);
        a.setHysteresisOn(hysteresisOn);
        a.setHysteresisOff(hysteresisOff);
        return a;
    }

    @Test
    void saveAcThresholds_savesOnAndOffThresholds() {
        when(applianceService.getApplianceByCode("AC")).thenReturn(Optional.of(ac(24.75, 0.5, 1.0)));

        scheduledService.saveAcThresholds();

        ArgumentCaptor<IndicationV3> captor = ArgumentCaptor.forClass(IndicationV3.class);
        verify(indicationServiceV3, times(2)).save(captor.capture());

        List<IndicationV3> saved = captor.getAllValues();
        IndicationV3 threshOn  = saved.stream().filter(i -> "AC-THRESHOLD-ON".equals(i.getLocationId())).findFirst().orElseThrow();
        IndicationV3 threshOff = saved.stream().filter(i -> "AC-THRESHOLD-OFF".equals(i.getLocationId())).findFirst().orElseThrow();

        assertThat(threshOn.getValue(),  is(25.25));   // 24.75 + 0.5
        assertThat(threshOff.getValue(), is(23.75));   // 24.75 - 1.0
        assertThat(threshOn.getMeasurementType(),  is("temp"));
        assertThat(threshOff.getMeasurementType(), is("temp"));
    }

    @Test
    void saveAcThresholds_acNotFound_savesNothing() {
        when(applianceService.getApplianceByCode("AC")).thenReturn(Optional.empty());

        scheduledService.saveAcThresholds();

        verify(indicationServiceV3, never()).save(any());
    }

    @Test
    void saveAcThresholds_settingChanges_thresholdsFollowSetting() {
        when(applianceService.getApplianceByCode("AC")).thenReturn(Optional.of(ac(26.0, 0.5, 1.0)));

        scheduledService.saveAcThresholds();

        ArgumentCaptor<IndicationV3> captor = ArgumentCaptor.forClass(IndicationV3.class);
        verify(indicationServiceV3, times(2)).save(captor.capture());

        List<IndicationV3> saved = captor.getAllValues();
        double on  = saved.stream().filter(i -> "AC-THRESHOLD-ON".equals(i.getLocationId())).findFirst().orElseThrow().getValue();
        double off = saved.stream().filter(i -> "AC-THRESHOLD-OFF".equals(i.getLocationId())).findFirst().orElseThrow().getValue();

        assertThat(on,  is(26.5));   // 26.0 + 0.5
        assertThat(off, is(25.0));   // 26.0 - 1.0
    }

    @Test
    void saveAcThresholds_withScheduledHysteresis() {
        Appliance a = ac(25.0, 0.5, 1.0);
        a.setSchedule(java.util.Map.of(
            "*", java.util.Map.of(
                String.valueOf(java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York")).getHour()), "/2.0/3.0"
            )
        ));

        when(applianceService.getApplianceByCode("AC")).thenReturn(Optional.of(a));

        scheduledService.saveAcThresholds();

        ArgumentCaptor<IndicationV3> captor = ArgumentCaptor.forClass(IndicationV3.class);
        verify(indicationServiceV3, times(2)).save(captor.capture());

        List<IndicationV3> saved = captor.getAllValues();
        double on  = saved.stream().filter(i -> "AC-THRESHOLD-ON".equals(i.getLocationId())).findFirst().orElseThrow().getValue();
        double off = saved.stream().filter(i -> "AC-THRESHOLD-OFF".equals(i.getLocationId())).findFirst().orElseThrow().getValue();

        assertThat(on,  is(27.0));   // 25.0 + 2.0
        assertThat(off, is(22.0));   // 25.0 - 3.0
    }
}
