package com.alexsoft.smarthouse.dto.mapper.v2;

import com.alexsoft.smarthouse.dto.v2.WindDto;
import com.alexsoft.smarthouse.model.messaging.Wind;
import org.mapstruct.Mapper;

@Mapper
public interface WindMapper {
    WindDto toDto(Wind wind);
    Wind toEntity(WindDto windDto);
}
