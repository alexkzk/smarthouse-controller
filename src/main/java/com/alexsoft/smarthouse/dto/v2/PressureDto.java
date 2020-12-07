package com.alexsoft.smarthouse.dto.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PressureDto {
    private Integer id;
    private Integer mmHg;
}
