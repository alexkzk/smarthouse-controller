package com.alexsoft.smarthouse.dto;

import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatIndicationDto {

    private Integer id;
    private MeasurePlace measurePlace;
    private Double tempCelsius;
    private Double relativeHumidity;
    private Double absoluteHumidity;

}
