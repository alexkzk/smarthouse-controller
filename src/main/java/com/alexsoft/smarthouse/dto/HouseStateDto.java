package com.alexsoft.smarthouse.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @lombok.Builder.Default
    private List<AirQualityIndicationDto> airQualities = new ArrayList<>();
    @lombok.Builder.Default
    private List<HeatIndicationDto> heatIndications = new ArrayList<>();
    @lombok.Builder.Default
    private List<WindIndicationsDto> windIndications = new ArrayList<>();

}
