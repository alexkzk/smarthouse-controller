package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entityv2.HouseState2;
import com.alexsoft.smarthouse.db.repository.HouseState2Repository;
import com.alexsoft.smarthouse.dto.mapper.HouseStateMessageMapper;
import com.alexsoft.smarthouse.messaging.model.HouseStateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HouseState2Service {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseState2Service.class);

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;
    private final HouseState2Repository houseState2Repository;
    private final HouseStateMessageMapper houseStateMessageMapper;

    public HouseState2 save(String msg) {
        if (msgSavingEnabled) {
            HouseStateMessage houseStateMessage;
            try {
                houseStateMessage = OBJECT_MAPPER.readValue(msg, HouseStateMessage.class);
                HouseState2 houseState2 = houseStateMessageMapper.toHouseState2(houseStateMessage);
                return houseState2Repository.saveAndFlush(houseState2);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
    }

}
