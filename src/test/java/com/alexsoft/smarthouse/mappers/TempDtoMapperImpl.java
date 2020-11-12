package com.alexsoft.smarthouse.mappers;

import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.dto.HeatIndicationDto;
import com.alexsoft.smarthouse.dto.HeatIndicationDto.HeatIndicationDtoBuilder;
import com.alexsoft.smarthouse.dto.mapper.TempDtoMapper;

public class TempDtoMapperImpl implements TempDtoMapper {

    @Override
    public HeatIndicationDto toDto(HeatIndication heatIndication) {
        if ( heatIndication == null ) {
            return null;
        }

        HeatIndicationDtoBuilder heatIndicationDto = HeatIndicationDto.builder();

        heatIndicationDto.measurePlace( heatIndication.getMeasurePlace() );
        heatIndicationDto.tempCelsius( heatIndication.getTempCelsius() );
        heatIndicationDto.relativeHumidity( heatIndication.getRelativeHumidity() );
        heatIndicationDto.absoluteHumidity( heatIndication.getAbsoluteHumidity() );

        return heatIndicationDto.build();
    }
}
