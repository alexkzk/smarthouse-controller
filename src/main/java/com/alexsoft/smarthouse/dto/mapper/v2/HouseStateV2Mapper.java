package com.alexsoft.smarthouse.dto.mapper.v2;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.v2.HouseStateV2;
import com.alexsoft.smarthouse.dto.v2.HouseStateV2Dto;
import org.mapstruct.Mapper;

@Mapper(uses = AirMapper.class)
public interface HouseStateV2Mapper {
    List<HouseStateV2Dto> toDtos(List<HouseStateV2> houseStateV2s);
    HouseStateV2Dto toDto(HouseStateV2 houseStateV2);
}
