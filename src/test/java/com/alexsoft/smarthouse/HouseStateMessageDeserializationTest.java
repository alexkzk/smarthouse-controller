package com.alexsoft.smarthouse;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.messaging.model.HouseStateMessage;
import com.alexsoft.smarthouse.utils.SerializationUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;

public class HouseStateMessageDeserializationTest {

    @Test
    public void houseStateMessageDeserializationTest() {
        HouseStateMessage houseStateMessage = SerializationUtils.deSerializeFromFile("mqtt-message.json",
            new TypeReference<HouseStateMessage>() {}, true);
        System.out.println();
    }

}
