package com.alexsoft.smarthouse.dto.v1;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    public HeatIndicationDto getOutdoorHeat() {
        return heatIndications.stream().filter(hi -> hi.getMeasurePlace() == MeasurePlace.BALCONY).findFirst()
            .orElse(HeatIndicationDto.builder().build());
    }

    @JsonIgnore
    public HeatIndicationDto getOutdoorAirportHeat() {
        return heatIndications.stream().filter(hi -> hi.getMeasurePlace() == MeasurePlace.CHERNIVTSI_AIRPORT).findFirst()
            .orElse(HeatIndicationDto.builder().build());
    }

    @JsonIgnore
    public HeatIndicationDto getIndoorHeat() {
        return heatIndications.stream().filter(hi -> hi.getMeasurePlace() == MeasurePlace.LIVING_ROOM).findFirst()
            .orElse(HeatIndicationDto.builder().build());
    }

    @JsonIgnore
    public HeatIndicationDto getAirportHeat() {
        return heatIndications.stream().filter(hi -> hi.getMeasurePlace() == MeasurePlace.CHERNIVTSI_AIRPORT).findFirst()
                .orElse(HeatIndicationDto.builder().build());
    }

    @JsonIgnore
    public AirQualityIndicationDto getOutdoorAqi() {
        return airQualities.stream().filter(aqi -> aqi.getMeasurePlace() == MeasurePlace.OUTDOOR).findFirst()
                .orElse(AirQualityIndicationDto.builder().build());
    }

    @JsonIgnore
    public WindIndicationsDto getWindIndications() {
        return windIndications.stream().findFirst().orElse(WindIndicationsDto.builder().build());
    }

}
