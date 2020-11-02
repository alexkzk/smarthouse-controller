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
@NoArgsConstructor
@Table(schema = "main")
public class HeatIndication extends Measure {

    @Builder
    public HeatIndication(
        final MeasurePlace measurePlace, final HouseState houseState, final Integer id,
        final Float tempCelsius, final Integer relativeHumidity, final Float absoluteHumidity
    ) {
        super(measurePlace, houseState);
        this.id = id;
        this.tempCelsius = tempCelsius;
        this.relativeHumidity = relativeHumidity;
        this.absoluteHumidity = absoluteHumidity;
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
        return (tempCelsius == null || tempCelsius == -100.0) && (relativeHumidity == null || relativeHumidity == -100.0)
            && (absoluteHumidity == null || absoluteHumidity == -100.0);
    }

}
