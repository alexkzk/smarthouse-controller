package com.alexsoft.smarthouse.dto;

import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatIndicationDto {

    private MeasurePlace measurePlace;
    private Float tempCelsius;
    private Integer relativeHumidity;
    private Float absoluteHumidity;

}
