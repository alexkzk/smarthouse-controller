package com.alexsoft.smarthouse.dto.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityDto {

    private Integer id;
    private Integer iaq;
    private Double pm25;
    private Double pm10;

}
