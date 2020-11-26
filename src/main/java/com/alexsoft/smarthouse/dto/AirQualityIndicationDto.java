package com.alexsoft.smarthouse.dto;

import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    public Integer getRawIaq() {
        return gasResistance == null ? null : (int) Math.round((700 - Math.round(gasResistance / 1000)) / 3.0);
    }

    @JsonIgnore
    public Integer getIaqInt() {
        return iaq == null ? null : (int) Math.round(iaq);
    }

    @JsonIgnore
    public Integer getStaticIaqInt() {
        return staticIaq == null ? null : (int) Math.round(staticIaq);
    }
}
