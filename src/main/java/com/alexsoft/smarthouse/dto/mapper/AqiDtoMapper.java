package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.dto.AirQualityIndicationDto;
import org.mapstruct.Mapper;

@Mapper
public interface AqiDtoMapper {
    AirQualityIndicationDto toDto(AirQualityIndication airQualityIndication);
}
