package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import com.alexsoft.smarthouse.db.repository.HouseStateV2Repository;
import com.alexsoft.smarthouse.dto.mapper.HouseStateMessageMapper;
import com.alexsoft.smarthouse.model.messaging.HouseStateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HouseStateV2Service {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseStateV2Service.class);

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;
    private final HouseStateV2Repository houseStateV2Repository;
    private final HouseStateMessageMapper houseStateMessageMapper;

    public HouseStateV2 save(String msg) {
        if (msgSavingEnabled) {
            HouseStateMessage houseStateMessage;
            try {
                houseStateMessage = OBJECT_MAPPER.readValue(msg, HouseStateMessage.class);
                HouseStateV2 houseStateV2 = houseStateMessageMapper.toHouseState2(houseStateMessage);
                return houseStateV2Repository.saveAndFlush(houseStateV2);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
    }

}
