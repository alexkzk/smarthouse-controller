package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.utils.DateUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class DateUtilsTest {

    private final ZoneId userTimezone = ZoneId.of("Europe/Kiev");
    private final DateTimeFormatter chartDateTimePattern = DateTimeFormatter.ofPattern("E d, HH:mm");
    private final DateUtils dateUtils = new DateUtils(userTimezone, chartDateTimePattern);

    @ParameterizedTest
    @MethodSource
    public void roundTest(int amount, TemporalUnit temporalUnit, String input, String expectedResult) {
        assertThat(dateUtils.roundDateTime(
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
                arguments(3, ChronoUnit.HOURS, "2021-10-26T18:00:00.000000", "2021-10-26T18:00")

        );
    }
}
