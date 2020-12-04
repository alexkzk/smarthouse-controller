package com.alexsoft.smarthouse.dto.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempDto {
    private Integer id;
    private Double celsius;
    private Integer rh;
    private Double ah;
}
