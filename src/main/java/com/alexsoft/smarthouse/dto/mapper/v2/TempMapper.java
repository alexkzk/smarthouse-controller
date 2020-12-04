package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.dto.v2.TempDto;
import com.alexsoft.smarthouse.model.messaging.Temp;
import org.mapstruct.Mapper;

@Mapper
public interface TempMapper {
    TempDto toDto(Temp temp);
    Temp toEntity(TempDto tempDto);
}
