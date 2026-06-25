package com.alexsoft.smarthouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.stream.Stream;

import static com.alexsoft.smarthouse.core.util.DateUtils.isDark;
import static com.alexsoft.smarthouse.core.util.DateUtils.roundDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class DateUtilsTest {

    // Miami sunrise in May ≈ 06:28 ET, sunset ≈ 19:53 ET

    @Test
    void isDark_middleOfNight_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T02:00")), is(true));
    }

    @Test
    void isDark_beforeSunrise_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T05:00")), is(true));
    }

    @Test
    void isDark_morningAfterSunrise_returnsFalse() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T09:00")), is(false));
    }

    @Test
    void isDark_afternoon_returnsFalse() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T14:00")), is(false));
    }

    @Test
    void isDark_eveningBeforeSunset_returnsFalse() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T18:00")), is(false));
    }

    @Test
    void isDark_lateEveningAfterSunset_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T21:00")), is(true));
    }

    @Test
    void isDark_justBeforeMidnight_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T23:59")), is(true));
    }

    @Test
    void isDark_justAfterMidnight_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-04T00:01")), is(true));
    }

    // Regression test for the specific bug: 11:46 PM ET was incorrectly returning false,
    // causing the http OFF command to skip the lock-until-morning rule.
    @Test
    void isDark_regressionBugAt2346_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-05-03T23:46")), is(true));
    }

    @Test
    void isDark_winterMiddleOfDay_returnsFalse() {
        assertThat(isDark(LocalDateTime.parse("2026-01-15T13:00")), is(false));
    }

    @Test
    void isDark_winterEarlyMorning_returnsTrue() {
        assertThat(isDark(LocalDateTime.parse("2026-01-15T06:00")), is(true));
    }

    @ParameterizedTest
    @MethodSource
    public void roundTest(int amount, TemporalUnit temporalUnit, String input, String expectedResult) {
        assertThat(roundDateTime(
                LocalDateTime.parse(input), amount, temporalUnit),
                is(LocalDateTime.parse(expectedResult))
        );
    }

    private static Stream<Arguments> roundTest() {
        return Stream.of(
                arguments(5, ChronoUnit.MINUTES, "2021-10-26T19:25:00.000000", "2021-10-26T19:25"),
                arguments(5, ChronoUnit.MINUTES, "2021-10-26T19:26:59.307543", "2021-10-26T19:25"),
                arguments(5, ChronoUnit.MINUTES, "2021-10-26T19:29:59.999999", "2021-10-26T19:25"),
                arguments(5, ChronoUnit.MINUTES, "2021-10-26T19:30:00.000000", "2021-10-26T19:30"),

                arguments(1, ChronoUnit.HOURS, "2021-10-26T16:00:00.000000", "2021-10-26T16:00"),
                arguments(1, ChronoUnit.HOURS, "2021-10-26T16:26:59.307543", "2021-10-26T16:00"),
                arguments(1, ChronoUnit.HOURS, "2021-10-26T16:59:59.999999", "2021-10-26T16:00"),
                arguments(1, ChronoUnit.HOURS, "2021-10-26T17:00:00.000000", "2021-10-26T17:00"),

                arguments(2, ChronoUnit.HOURS, "2021-10-26T16:00:00.000000", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T16:26:59.307543", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T16:59:59.999999", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T17:00:00.000000", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T17:26:59.307543", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T17:59:59.999999", "2021-10-26T16:00"),
                arguments(2, ChronoUnit.HOURS, "2021-10-26T18:00:00.000000", "2021-10-26T18:00"),

                arguments(3, ChronoUnit.HOURS, "2021-10-26T15:00:00.000000", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T15:26:59.307543", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T15:59:59.999999", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T16:00:00.000000", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T16:26:59.307543", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T16:59:59.999999", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T17:00:00.000000", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T17:26:59.307543", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T17:59:59.999999", "2021-10-26T15:00"),
                arguments(3, ChronoUnit.HOURS, "2021-10-26T18:00:00.000000", "2021-10-26T18:00"),

                arguments(1, ChronoUnit.DAYS, "2021-10-26T18:00:00.000000", "2021-10-26T00:00")

        );
    }
}
