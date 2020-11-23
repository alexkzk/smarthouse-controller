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
    private Float staticIaq;
    private Integer iaqAccuracy;
    private Float gasResistance;
    private Integer maxIaq;
    private Float co2;
    private Float voc;

    public Integer getIntGasResistance() {
        return gasResistance == null ? null : (int) Math.round(gasResistance / 1000);
    }
}
