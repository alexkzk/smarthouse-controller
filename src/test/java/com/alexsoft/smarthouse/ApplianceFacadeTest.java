package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.ApplianceGroup;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.service.ApplianceFacade;
import com.alexsoft.smarthouse.service.IndicationServiceV3;
import com.alexsoft.smarthouse.service.MessageSenderService;
import com.alexsoft.smarthouse.utils.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplianceFacadeTest {

    @Mock ApplianceRepository applianceRepository;
    @Mock MessageSenderService messageSenderService;
    @Mock IndicationRepositoryV3 indicationRepositoryV3;
    @Mock EventRepository eventRepository;
    @Mock IndicationServiceV3 indicationServiceV3;

    @InjectMocks ApplianceFacade facade;

    private static final LocalDateTime UTC = LocalDateTime.of(2026, 4, 7, 11, 22, 0);

    private Appliance ambientGroupLight(com.alexsoft.smarthouse.enums.ApplianceState initialState) {
        ApplianceGroup group = new ApplianceGroup();
        group.setId(1);
        Appliance a = new Appliance();
        a.setCode("MB-LOB");
        a.setApplianceGroup(group);
        a.setState(initialState, UTC.minusHours(1));
        a.setMetricType("illuminance");
        return a;
    }

    private Appliance nonGroupAppliance(com.alexsoft.smarthouse.enums.ApplianceState initialState) {
        Appliance a = new Appliance();
        a.setCode("DEH");
        a.setState(initialState, UTC.minusHours(1));
        return a;
    }

    // Rule 1: dark + turned OFF via http → lock until wakeUpTime
    @Test
    void rule1_darkTurnedOff_locksUntilWakeUpTime() {
        LocalDateTime wakeUpTime = LocalDateTime.of(2026, 4, 8, 10, 55);
        Appliance light = ambientGroupLight(ON);

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);
            dateUtils.when(DateUtils::wakeUpTime).thenReturn(wakeUpTime);

            facade.toggle(light, OFF, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(OFF));
        assertThat(light.isLocked(), is(true));
        assertThat(light.getLockedUntilUtc(), is(wakeUpTime));
        verify(eventRepository).save(argThat(e -> "locked-until".equals(e.getType())
                && Integer.valueOf(1).equals(e.getData().get("rule"))));
    }

    // Rule 1 via zigbee trigger — same locking behavior
    @Test
    void rule1_darkTurnedOffViaZigbee_locksUntilWakeUpTime() {
        LocalDateTime wakeUpTime = LocalDateTime.of(2026, 4, 8, 10, 55);
        Appliance light = ambientGroupLight(ON);

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);
            dateUtils.when(DateUtils::wakeUpTime).thenReturn(wakeUpTime);

            facade.toggle(light, OFF, UTC, "zigbee2mqtt/mb-led-over-bed", false);
        }

        assertThat(light.getState(), is(OFF));
        assertThat(light.isLocked(), is(true));
        assertThat(light.getLockedUntilUtc(), is(wakeUpTime));
    }

    // lock.preserved: existing lock is later than wakeUpTime → do not overwrite
    @Test
    void rule1_existingLockIsLater_preserves() {
        LocalDateTime wakeUpTime = LocalDateTime.of(2026, 4, 8, 10, 55);
        LocalDateTime laterLock  = LocalDateTime.of(2026, 4, 9, 10, 55);
        Appliance light = ambientGroupLight(ON);
        light.setLocked(true);
        light.setLockedUntilUtc(laterLock);

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);
            dateUtils.when(DateUtils::wakeUpTime).thenReturn(wakeUpTime);

            facade.toggle(light, OFF, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(OFF));
        assertThat(light.getLockedUntilUtc(), is(laterLock));
        verify(eventRepository).save(argThat(e -> "lock.preserved".equals(e.getType())));
    }

    // Rule 2: dark + turned ON via http → unlock
    @Test
    void rule2_darkTurnedOn_unlocks() {
        Appliance light = ambientGroupLight(OFF);
        light.setLocked(true);
        light.setLockedUntilUtc(LocalDateTime.of(2026, 4, 8, 10, 55));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);

            facade.toggle(light, ON, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(ON));
        assertThat(light.isLocked(), is(false));
        assertThat(light.getLockedUntilUtc(), is(nullValue()));
        verify(eventRepository).save(argThat(e -> "unlocked".equals(e.getType())
                && Integer.valueOf(2).equals(e.getData().get("rule"))));
    }

    // Rule 4a: not dark + turned OFF via http → unlock
    @Test
    void rule4a_notDarkTurnedOff_unlocks() {
        Appliance light = ambientGroupLight(ON);

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(false);

            facade.toggle(light, OFF, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(OFF));
        assertThat(light.isLocked(), is(false));
        verify(eventRepository).save(argThat(e -> "unlocked".equals(e.getType())
                && Integer.valueOf(4).equals(e.getData().get("rule"))));
    }

    // Rule 4b: not dark + turned ON via http → lock until sunset+1h
    @Test
    void rule4b_notDarkTurnedOn_locksUntilSunsetPlusOneHour() {
        LocalDateTime sunsetEastern  = LocalDateTime.of(2026, 4, 7, 19, 30);
        LocalDateTime expectedLockUtc = DateUtils.toUtc(sunsetEastern.plusHours(1));
        Appliance light = ambientGroupLight(OFF);

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(false);
            dateUtils.when(DateUtils::getNearestSunsetTime).thenReturn(sunsetEastern);

            facade.toggle(light, ON, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(ON));
        assertThat(light.isLocked(), is(true));
        assertThat(light.getLockedUntilUtc(), is(expectedLockUtc));
        verify(eventRepository).save(argThat(e -> "locked-until".equals(e.getType())
                && Integer.valueOf(4).equals(e.getData().get("rule"))));
    }

    // Rule 5: non-group appliance switched OFF with minimumOffCycleMinutes → lock for that duration
    @Test
    void rule5_nonGroupSwitchedOff_locksForMinimumOffCycle() {
        Appliance appliance = nonGroupAppliance(ON);
        appliance.setMinimumOffCycleMinutes(10);

        facade.toggle(appliance, OFF, UTC, "http-controller", false);

        assertThat(appliance.getState(), is(OFF));
        assertThat(appliance.isLocked(), is(true));
        assertThat(appliance.getLockedUntilUtc(), is(UTC.plusMinutes(10)));
        verify(eventRepository).save(argThat(e -> "locked-until".equals(e.getType())
                && Integer.valueOf(5).equals(e.getData().get("rule"))));
    }

    // Rule 6: non-group appliance switched ON with minimumOnCycleMinutes → lock for that duration
    @Test
    void rule6_nonGroupSwitchedOn_locksForMinimumOnCycle() {
        Appliance appliance = nonGroupAppliance(OFF);
        appliance.setMinimumOnCycleMinutes(30);

        facade.toggle(appliance, ON, UTC, "http-controller", false);

        assertThat(appliance.getState(), is(ON));
        assertThat(appliance.isLocked(), is(true));
        assertThat(appliance.getLockedUntilUtc(), is(UTC.plusMinutes(30)));
        verify(eventRepository).save(argThat(e -> "locked-until".equals(e.getType())
                && Integer.valueOf(6).equals(e.getData().get("rule"))));
    }

    // HTTP toggle ignores existing lock — lock is only for pwr-control
    @Test
    void httpToggle_ignoresExistingLock() {
        Appliance light = ambientGroupLight(ON);
        light.setLocked(true);
        light.setLockedUntilUtc(LocalDateTime.of(2026, 4, 8, 10, 55));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);
            dateUtils.when(DateUtils::wakeUpTime).thenReturn(LocalDateTime.of(2026, 4, 8, 10, 55));

            facade.toggle(light, OFF, UTC, "http-controller", false);
        }

        assertThat(light.getState(), is(OFF));
    }

    // Zigbee toggle ignores existing lock — lock is only for pwr-control
    @Test
    void zigbeeToggle_ignoresExistingLock() {
        Appliance light = ambientGroupLight(ON);
        light.setLocked(true);
        light.setLockedUntilUtc(LocalDateTime.of(2026, 4, 8, 10, 55));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class, CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::isDark).thenReturn(true);
            dateUtils.when(DateUtils::wakeUpTime).thenReturn(LocalDateTime.of(2026, 4, 8, 10, 55));

            facade.toggle(light, OFF, UTC, "zigbee2mqtt/mb-led-over-bed", false);
        }

        assertThat(light.getState(), is(OFF));
    }

    // No lock set when state doesn't change
    @Test
    void noLock_whenStateUnchanged() {
        Appliance appliance = nonGroupAppliance(OFF);
        appliance.setMinimumOffCycleMinutes(10);

        facade.toggle(appliance, OFF, UTC, "pwr-control", false);

        assertThat(appliance.getState(), is(OFF));
        assertThat(appliance.isLocked(), is(false));
        verify(eventRepository, never()).save(argThat(e -> "locked-until".equals(e.getType())));
    }
}
