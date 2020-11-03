package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@Table(schema = "main")
public class HeatIndication extends Measure {

    private static final int TEMP_ACCURACY = 10;
    private static final int AH_ACCURACY = 100;

    @Builder
    public HeatIndication(
        final MeasurePlace measurePlace, final HouseState houseState, final Integer id,
        final Float tempCelsius, final Integer relativeHumidity, final Float absoluteHumidity
    ) {
        super(measurePlace, houseState);
        this.id = id;
        this.tempCelsius = tempCelsius == null || Float.isNaN(tempCelsius) ? null : (float) Math.round(tempCelsius * TEMP_ACCURACY) / TEMP_ACCURACY;
        this.relativeHumidity = relativeHumidity;
        this.absoluteHumidity = absoluteHumidity == null || Float.isNaN(absoluteHumidity) ? null : (float) Math.round(absoluteHumidity * AH_ACCURACY) / AH_ACCURACY;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "heat_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "heat_indication_sq", name = "heat_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Float tempCelsius;

    private Integer relativeHumidity;

    private Float absoluteHumidity;

    @JsonIgnore
    public boolean isNull() {
        return (tempCelsius == null || Float.isNaN(tempCelsius)) && (relativeHumidity == null)
            && (absoluteHumidity == null || Float.isNaN(absoluteHumidity));
    }

}
