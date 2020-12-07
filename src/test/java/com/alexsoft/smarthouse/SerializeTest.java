package com.alexsoft.smarthouse;

import java.util.List;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.utils.SerializationUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SerializeTest {

    @Test
    public void localDateTimeSerializeDeserializeTest() {   //  Deserialization smoke test

        List<HouseState> houseStates = SerializationUtils.deSerializeFromFile("measures-list.json",
            new TypeReference<List<HouseState>>() {}, true);

        List<Object> measures = houseStates.stream().flatMap(houseState -> StreamEx.of(
            houseState.getHeatIndications().stream(),
            houseState.getWindIndications().stream(),
            houseState.getAirQualities().stream()
        )).collect(Collectors.toList());

        assertThat(measures.size(), is(174));
    }
}
