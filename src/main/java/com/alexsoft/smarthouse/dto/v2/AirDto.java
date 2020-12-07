package com.alexsoft.smarthouse.dto.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirDto {
    private TempDto temp;
    private QualityDto quality;
    private PressureDto pressure;
    private WindDto wind;
}
