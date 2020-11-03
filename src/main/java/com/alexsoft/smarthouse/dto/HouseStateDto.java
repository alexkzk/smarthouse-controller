package com.alexsoft.smarthouse.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseStateDto {

    private Integer id;
    private LocalDateTime messageIssued;
    private LocalDateTime messageReceived;
    private List<AirQualityIndicationDto> airQualities;
    private List<HeatIndicationDto> heatIndications;
    private List<WindIndicationsDto> windIndications;

}
