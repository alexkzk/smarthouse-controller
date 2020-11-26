package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import com.alexsoft.smarthouse.messaging.model.HouseStateMessage;
import org.mapstruct.Mapper;

@Mapper
public interface HouseStateMessageMapper {
    HouseStateV2 toHouseState2(HouseStateMessage houseStateMessage);
}
