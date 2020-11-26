package com.alexsoft.smarthouse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.alexsoft.smarthouse.db.entity.v1.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.AirQualityIndicationDto;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import com.alexsoft.smarthouse.mappers.HouseStateToDtoMapperImpl;
import com.alexsoft.smarthouse.service.HouseStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HouseStateServiceTest {

    @Mock
    private HouseStateToDtoMapper houseStateToDtoMapper;

    @Mock
    private HouseStateRepository houseStateRepository;

    private HouseStateService houseStateService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        when(houseStateToDtoMapper.toDto(any())).thenAnswer(i -> new HouseStateToDtoMapperImpl().toDto((HouseState) i.getArguments()[0]));
        houseStateService = new HouseStateService(null, houseStateRepository, houseStateToDtoMapper, null);
    }

    @Test
    public void houseStateAveragingTest () {
        List<HouseState> states = Arrays.asList(
            HouseState.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 1)).airQualities(
                Collections.singletonList(AirQualityIndication.builder().pm10(5.0F).pm25(7.0F).measurePlace(MeasurePlace.TERRACE_ROOF).build())
            ).build(),
            HouseState.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 5)).airQualities(
                Collections.singletonList(AirQualityIndication.builder().pm10(6.0F).pm25(8.0F).measurePlace(MeasurePlace.TERRACE_ROOF).build())
            ).build(),
            HouseState.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 11)).airQualities(
                Collections.singletonList(AirQualityIndication.builder().pm10(6.0F).measurePlace(MeasurePlace.TERRACE_ROOF).build())
            ).build(),
            HouseState.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 11)).airQualities(
                Collections.singletonList(AirQualityIndication.builder().pm10(8.0F).pm25(3.0F).measurePlace(MeasurePlace.TERRACE_ROOF).build())
            ).build()
        );
        when(houseStateRepository.findAfter(any())).thenReturn(states);
        assertThat(houseStateService.aggregateOnInterval(10, null, null, null), is(
                Arrays.asList(
                    HouseStateDto.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 10)).airQualities(
                        Collections.singletonList(AirQualityIndicationDto.builder().pm10(7.0F).pm25(3.0F).measurePlace(MeasurePlace.OUTDOOR).build())
                    ).build(),
                    HouseStateDto.builder().messageReceived(LocalDateTime.of(2020, 11, 1, 10, 0)).airQualities(Arrays.asList(
                        AirQualityIndicationDto.builder().measurePlace(MeasurePlace.OUTDOOR).pm10(5.5F).pm25(7.5F).build()
                    )).build()
                )
            )
        );

    }

}
