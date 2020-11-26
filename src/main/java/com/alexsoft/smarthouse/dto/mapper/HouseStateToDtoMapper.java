package com.alexsoft.smarthouse.dto.mapper;

import java.util.List;

import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import org.mapstruct.Mapper;

@Mapper(uses = {AqiDtoMapper.class, TempDtoMapper.class})
public interface HouseStateToDtoMapper {
    List<HouseStateDto> toDtos(List<HouseState> houseStates);
    HouseStateDto toDto(HouseState houseState);
}
