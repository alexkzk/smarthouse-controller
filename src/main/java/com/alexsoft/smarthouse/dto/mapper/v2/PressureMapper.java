package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.dto.v2.PressureDto;
import com.alexsoft.smarthouse.model.messaging.Pressure;
import org.mapstruct.Mapper;

@Mapper
public interface PressureMapper {
    PressureDto toDto(Pressure pressure);
    Pressure toEntity(PressureDto pressureDto);
}
