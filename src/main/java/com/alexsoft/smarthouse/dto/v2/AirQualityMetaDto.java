package com.alexsoft.smarthouse.dto.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirQualityMetaDto {
    private Integer id;
    private Integer bme680GasResistance;
    private Integer bme680Co2;
    private Double bme680Voc;
    private Integer bme680IaqAccuracy;
    private Integer bme680StaticIaq;
    private Double bme680RawTemp;
    private Integer bme680RawRh;
}
