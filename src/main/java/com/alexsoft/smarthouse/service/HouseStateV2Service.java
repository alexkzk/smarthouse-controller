package com.alexsoft.smarthouse.service;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.InOut;
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
    public static final String IN_PREFIX = "IN-";
    public static final String OUT_PREFIX = "OUT-";

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;
    private final HouseStateV2Repository houseStateV2Repository;
    private final HouseStateMessageMapper houseStateMessageMapper;
    private final HouseStateV2Mapper houseStateV2Mapper;

    public HouseStateV2 save(String msg) {
        HouseStateV2 houseStateV2 = null;
        try {
            HouseStateMessage houseStateMessage = OBJECT_MAPPER.readValue(msg, HouseStateMessage.class);
            houseStateV2 = houseStateMessageMapper.toHouseState2(houseStateMessage);
            if (houseStateV2.getMeasurePlace().startsWith(IN_PREFIX)) { //  todo temporary, remove after changing the msg format on publishers
                houseStateV2.setInOut(InOut.IN);
                houseStateV2.setMeasurePlace(houseStateV2.getMeasurePlace().replace(IN_PREFIX, ""));
            } else if (houseStateV2.getMeasurePlace().startsWith(OUT_PREFIX)) {
                houseStateV2.setInOut(InOut.OUT);
                houseStateV2.setMeasurePlace(houseStateV2.getMeasurePlace().replace(OUT_PREFIX, ""));
            }
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (msgSavingEnabled && houseStateV2 != null) {
            return houseStateV2Repository.saveAndFlush(houseStateV2);
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
