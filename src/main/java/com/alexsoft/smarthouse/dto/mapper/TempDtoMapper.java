package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.dto.TempDto;
import org.mapstruct.Mapper;

@Mapper
public interface TempDtoMapper {
    TempDto toDto(HeatIndication heatIndication);
}
