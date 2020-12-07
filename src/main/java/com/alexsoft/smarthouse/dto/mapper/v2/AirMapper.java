package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.db.entity.v2.Air;
import com.alexsoft.smarthouse.dto.v2.AirDto;
import org.mapstruct.Mapper;

@Mapper(uses = {PressureMapper.class, QualityMapper.class, TempMapper.class, WindMapper.class})
public interface AirMapper {
    AirDto toDto(Air air);
    Air toEntity(AirDto airDto);
}
