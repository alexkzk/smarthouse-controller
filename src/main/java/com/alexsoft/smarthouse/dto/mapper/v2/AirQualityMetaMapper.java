package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.db.entity.v2.Bme680Meta;
import com.alexsoft.smarthouse.dto.v2.AirQualityMetaDto;

public interface AirQualityMetaMapper {
    AirQualityMetaDto toDto(Bme680Meta bme680Meta);
    Bme680Meta toEntity(AirQualityMetaDto airQualityMetaDto);
}
