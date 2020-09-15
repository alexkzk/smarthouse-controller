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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main")
public class Aqi {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aqi_sq")
    @SequenceGenerator(schema = "main", sequenceName = "aqi_sq", name = "aqi_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Enumerated(EnumType.STRING)
    private MeasurePlace measurePlace;

    private Double pm25;

    private Double pm10;

    @ManyToOne
    private HouseState houseState;

    public boolean isNull() {
        return (pm25 == null || pm25 == -100.0) && (pm10 == null || pm10 == -100.0);
    }
}
