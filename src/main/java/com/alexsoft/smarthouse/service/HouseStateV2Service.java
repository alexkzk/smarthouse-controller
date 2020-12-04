package com.alexsoft.smarthouse.service;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import com.alexsoft.smarthouse.db.repository.HouseStateV2Repository;
import com.alexsoft.smarthouse.dto.mapper.HouseStateMessageMapper;
import com.alexsoft.smarthouse.dto.mapper.v2.HouseStateV2Mapper;
import com.alexsoft.smarthouse.dto.v2.HouseStateV2Dto;
import com.alexsoft.smarthouse.model.messaging.HouseStateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.utils.DateUtils.getInterval;

@Service
@RequiredArgsConstructor
public class HouseStateV2Service {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseStateV2Service.class);

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;
    private final HouseStateV2Repository houseStateV2Repository;
    private final HouseStateMessageMapper houseStateMessageMapper;
    private final HouseStateV2Mapper houseStateV2Mapper;

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

    public List<HouseStateV2Dto> findWithinInterval(final Integer minutes, final Integer hours, final Integer days) {
        return houseStateV2Mapper.toDtos(houseStateV2Repository.findAfter(getInterval(minutes, hours, days, true)));
    }

    public List<HouseStateV2Dto> findAll() {
        return houseStateV2Mapper.toDtos(houseStateV2Repository.findAll());
    }
}
