package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main")
public class HeatIndication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "heat_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "heat_indication_sq", name = "heat_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Enumerated(EnumType.STRING)
    private MeasurePlace measurePlace;

    private Float tempCelsius;

    private Integer relativeHumidity;

    private Float absoluteHumidity;

    @ManyToOne
    private HouseState houseState;

    public boolean isNull() {
        return (tempCelsius == null || tempCelsius == -100.0) && (relativeHumidity == null || relativeHumidity == -100.0) && (absoluteHumidity == null || absoluteHumidity == -100.0);
    }

}
