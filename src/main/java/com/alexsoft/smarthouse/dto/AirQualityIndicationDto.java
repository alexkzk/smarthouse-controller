package com.alexsoft.smarthouse.dto;

import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirQualityIndicationDto {

    private MeasurePlace measurePlace;
    private Float pm25;
    private Float pm10;
    private Float iaq;
    private Integer maxIaq;
    private Float co2;
    private Float voc;

}
