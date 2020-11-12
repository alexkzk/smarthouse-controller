package com.alexsoft.smarthouse.mappers;

import java.util.ArrayList;
import java.util.List;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.WindIndication;
import com.alexsoft.smarthouse.dto.AirQualityIndicationDto;
import com.alexsoft.smarthouse.dto.HeatIndicationDto;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.HouseStateDto.HouseStateDtoBuilder;
import com.alexsoft.smarthouse.dto.WindIndicationsDto;
import com.alexsoft.smarthouse.dto.WindIndicationsDto.WindIndicationsDtoBuilder;
import com.alexsoft.smarthouse.dto.mapper.AqiDtoMapper;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import com.alexsoft.smarthouse.dto.mapper.TempDtoMapper;

public class HouseStateToDtoMapperImpl implements HouseStateToDtoMapper {

    private AqiDtoMapper aqiDtoMapper = new AqiDtoMapperImpl();
    private TempDtoMapper tempDtoMapper = new TempDtoMapperImpl();

    @Override
    public List<HouseStateDto> toDtos(List<HouseState> houseStates) {
        if ( houseStates == null ) {
            return null;
        }

        List<HouseStateDto> list = new ArrayList<HouseStateDto>( houseStates.size() );
        for ( HouseState houseState : houseStates ) {
            list.add( toDto( houseState ) );
        }

        return list;
    }

    @Override
    public HouseStateDto toDto(HouseState houseState) {
        if ( houseState == null ) {
            return null;
        }

        HouseStateDtoBuilder houseStateDto = HouseStateDto.builder();

        houseStateDto.messageIssued( houseState.getMessageIssued() );
        houseStateDto.messageReceived( houseState.getMessageReceived() );
        houseStateDto.airQualities( airQualityIndicationListToAirQualityIndicationDtoList( houseState.getAirQualities() ) );
        houseStateDto.heatIndications( heatIndicationListToHeatIndicationDtoList( houseState.getHeatIndications() ) );
        houseStateDto.windIndications( windIndicationListToWindIndicationsDtoList( houseState.getWindIndications() ) );

        return houseStateDto.build();
    }

    protected List<AirQualityIndicationDto> airQualityIndicationListToAirQualityIndicationDtoList(List<AirQualityIndication> list) {
        if ( list == null ) {
            return null;
        }

        List<AirQualityIndicationDto> list1 = new ArrayList<AirQualityIndicationDto>( list.size() );
        for ( AirQualityIndication airQualityIndication : list ) {
            list1.add( aqiDtoMapper.toDto( airQualityIndication ) );
        }

        return list1;
    }

    protected List<HeatIndicationDto> heatIndicationListToHeatIndicationDtoList(List<HeatIndication> list) {
        if ( list == null ) {
            return null;
        }

        List<HeatIndicationDto> list1 = new ArrayList<HeatIndicationDto>( list.size() );
        for ( HeatIndication heatIndication : list ) {
            list1.add( tempDtoMapper.toDto( heatIndication ) );
        }

        return list1;
    }

    protected WindIndicationsDto windIndicationToWindIndicationsDto(WindIndication windIndication) {
        if ( windIndication == null ) {
            return null;
        }

        WindIndicationsDtoBuilder windIndicationsDto = WindIndicationsDto.builder();

        windIndicationsDto.measurePlace( windIndication.getMeasurePlace() );
        windIndicationsDto.direction( windIndication.getDirection() );
        windIndicationsDto.speed( windIndication.getSpeed() );

        return windIndicationsDto.build();
    }

    protected List<WindIndicationsDto> windIndicationListToWindIndicationsDtoList(List<WindIndication> list) {
        if ( list == null ) {
            return null;
        }

        List<WindIndicationsDto> list1 = new ArrayList<WindIndicationsDto>( list.size() );
        for ( WindIndication windIndication : list ) {
            list1.add( windIndicationToWindIndicationsDto( windIndication ) );
        }

        return list1;
    }
}
