package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.v1.AirQualityIndication;
import com.alexsoft.smarthouse.dto.v1.AirQualityIndicationDto;
import org.mapstruct.Mapper;

@Mapper
public interface AqiDtoMapper {
    AirQualityIndicationDto toDto(AirQualityIndication airQualityIndication);
}
