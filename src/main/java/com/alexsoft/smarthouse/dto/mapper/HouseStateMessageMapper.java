package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entityv2.HouseState2;
import com.alexsoft.smarthouse.messaging.model.HouseStateMessage;
import org.mapstruct.Mapper;

@Mapper
public interface HouseStateMessageMapper {
    HouseState2 toHouseState2(HouseStateMessage houseStateMessage);
}
