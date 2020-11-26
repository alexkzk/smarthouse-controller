package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.v1.HeatIndication;
import com.alexsoft.smarthouse.dto.HeatIndicationDto;
import org.mapstruct.Mapper;

@Mapper
public interface TempDtoMapper {
    HeatIndicationDto toDto(HeatIndication heatIndication);
}
