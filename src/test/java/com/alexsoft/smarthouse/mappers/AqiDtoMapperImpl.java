package com.alexsoft.smarthouse.mappers;


import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.dto.AirQualityIndicationDto;
import com.alexsoft.smarthouse.dto.AirQualityIndicationDto.AirQualityIndicationDtoBuilder;
import com.alexsoft.smarthouse.dto.mapper.AqiDtoMapper;

public class AqiDtoMapperImpl implements AqiDtoMapper {

    @Override
    public AirQualityIndicationDto toDto(AirQualityIndication airQualityIndication) {
        if ( airQualityIndication == null ) {
            return null;
        }

        AirQualityIndicationDtoBuilder airQualityIndicationDto = AirQualityIndicationDto.builder();

        airQualityIndicationDto.id( airQualityIndication.getId() );
        airQualityIndicationDto.measurePlace( airQualityIndication.getMeasurePlace() );
        airQualityIndicationDto.pm25( airQualityIndication.getPm25() );
        airQualityIndicationDto.pm10( airQualityIndication.getPm10() );

        return airQualityIndicationDto.build();
    }
}
