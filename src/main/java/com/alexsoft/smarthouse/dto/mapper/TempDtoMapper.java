package com.alexsoft.smarthouse.dto.mapper;

import com.alexsoft.smarthouse.db.entity.Temperature;
import com.alexsoft.smarthouse.dto.TempDto;
import org.mapstruct.Mapper;

@Mapper
public interface TempDtoMapper {
    TempDto toDto(Temperature temperature);
}
