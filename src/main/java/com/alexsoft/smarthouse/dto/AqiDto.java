package com.alexsoft.smarthouse.dto;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Builder
public class AqiDto {

    private Integer id;
    private MeasurePlace measurePlace;
    private Double pm25;
    private Double pm10;

}
