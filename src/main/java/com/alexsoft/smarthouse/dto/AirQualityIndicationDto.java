package com.alexsoft.smarthouse.dto;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirQualityIndicationDto {

    private Integer id;
    private MeasurePlace measurePlace;
    private Float pm25;
    private Float pm10;

}
