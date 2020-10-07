package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.dto.AqiDto;
import org.mapstruct.Mapper;

@Mapper
public interface AqiDtoMapper {
    AqiDto toDto(AirQualityIndication airQualityIndication);
}
