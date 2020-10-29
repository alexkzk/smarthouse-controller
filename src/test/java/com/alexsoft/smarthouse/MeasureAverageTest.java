package com.alexsoft.smarthouse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.HouseState;
import org.apache.commons.collections4.ListUtils;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MeasureAverageTest {

    @Test
    public void groupingTest() {

        /*List<HouseState> input1 = SerializationUtils.deSerializeFromFile("measures-list.json",
            new TypeReference<List<HouseState>>() {}, true);*/
        List<HouseState> input1 = List.of(
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 0, 0)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 2, 0)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 7, 0)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 9, 59)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 10, 0)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 10, 1)).build()
        );
        List<HouseState> input2 = List.of(
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 10, 0)).build(),
            HouseState.builder().messageIssued(LocalDateTime.of(2020, 1, 1, 10, 10, 1)).build()
        );
        Map<LocalDateTime, List<HouseState>> expectedMap = Map.of(
            LocalDateTime.of(2020, 1, 1, 10, 0, 0), input1,
            LocalDateTime.of(2020, 1, 1, 10, 10, 0), input2
        );
        Map<LocalDateTime, List<HouseState>> map = ListUtils.union(input1, input2).stream().collect(
            Collectors.groupingBy(
                houseState -> houseState.getMessageReceived().withSecond(0).withNano(0)
                    .plusMinutes((65 - houseState.getMessageReceived().getMinute()) % 5),
                TreeMap::new,
                toList()
            )
        );
        assertThat(map, is(expectedMap));

    }

}
