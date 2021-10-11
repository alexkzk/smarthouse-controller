package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Data;
import lombok.ToString;

@Data
@Entity
@Table(name = "air_pressure_indication", schema = "main")
public class Pressure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_pressure_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_pressure_indication_sq",
        name = "air_pressure_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Double mmHg;

    public boolean isEmpty() {
        return getMmHg() == null;
    }
}
