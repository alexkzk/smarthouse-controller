package com.alexsoft.smarthouse.dto;

import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindIndicationsDto {

    private MeasurePlace measurePlace;
    private Integer direction;
    private Integer speed;

}
