package com.alexsoft.smarthouse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.alexsoft.smarthouse.db.entity.HouseState;
import org.apache.commons.collections4.ListUtils;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MeasureAverageTest {

    @Test
    public void groupingTest() {

        List<HouseState> zeroMinutes = List.of(
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 0, 0)).build(),
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 2, 0)).build(),
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 7, 0)).build(),
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 9, 59)).build()
        );
        List<HouseState> tenMinutes = List.of(
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 10, 0)).build(),
            HouseState.builder().airQualities(List.of()).heatIndications(List.of()).windIndications(List.of()).messageReceived(LocalDateTime.of(2020, 1, 1, 10, 10, 1)).build()
        );
        Map<LocalDateTime, List<HouseState>> expectedMap = Map.of(
            LocalDateTime.of(2020, 1, 1, 10, 0, 0), zeroMinutes,
            LocalDateTime.of(2020, 1, 1, 10, 10, 0), tenMinutes
        );
        Map<LocalDateTime, List<HouseState>> actualMap = ListUtils.union(zeroMinutes, tenMinutes).stream().collect(
            Collectors.groupingBy(
                houseState -> houseState.getMessageReceived().withSecond(0).withNano(0)
                    .withMinute(houseState.getMessageReceived().getMinute() / 10 * 10),
                TreeMap::new,
                toList()
            )
        );
        assertThat(actualMap, is(expectedMap));

    }

    @Test
    public void intAverageTest() {
        List<Integer> list = Arrays.asList(0, 1, 2, 3, 4, null);
        Double averagedValue = list.stream().filter(Objects::nonNull).mapToDouble(Integer::intValue)
                .average().orElse(Double.NaN);
        assertThat(averagedValue, is(2.0));
    }

}
