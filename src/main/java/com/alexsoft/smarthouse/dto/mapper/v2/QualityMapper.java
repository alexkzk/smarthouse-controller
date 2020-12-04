package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.dto.v2.QualityDto;
import com.alexsoft.smarthouse.model.messaging.Quality;
import org.mapstruct.Mapper;

@Mapper(uses = AirQualityMetaMapper.class)
public interface QualityMapper {
    QualityDto toDto(Quality quality);
    Quality toEntity(QualityDto qualityDto);
}
