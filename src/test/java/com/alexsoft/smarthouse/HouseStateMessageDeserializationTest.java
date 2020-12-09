package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.model.messaging.HouseStateMessage;
import com.alexsoft.smarthouse.utils.SerializationUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;

public class HouseStateMessageDeserializationTest {

    @Test
    public void houseStateMessageDeserializationTest() {
        HouseStateMessage houseStateMessage = SerializationUtils.deSerializeFromFile("mqtt-message.json",
            new TypeReference<HouseStateMessage>() {}, true);
    }

}
