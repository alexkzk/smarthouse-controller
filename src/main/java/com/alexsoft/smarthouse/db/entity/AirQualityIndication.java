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
public class AirQualityIndication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_quality_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_quality_indication_sq",
        name = "air_quality_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Enumerated(EnumType.STRING)
    private MeasurePlace measurePlace;

    private Float pm25;

    private Float pm10;

    @ToString.Exclude
    @ManyToOne
    private HouseState houseState;

    public boolean isNull() {
        return (pm25 == null || pm25 == -100.0) && (pm10 == null || pm10 == -100.0);
    }
}
