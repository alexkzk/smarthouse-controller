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
@Table(name = "air_temp_indication", schema = "main")
public class Temp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_temp_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_temp_indication_sq", name = "air_temp_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Double celsius;

    private Integer rh;

    private Double ah;

    public boolean isEmpty() {
        return getCelsius() == null && getRh() == null && getAh() == null;
    }

    public boolean normalize() {
        boolean normalized = false;
        if (celsius != null && celsius > 50D) {
            celsius = 50D;
            normalized = true;
        }
        if (celsius != null && celsius < -50D) {
            celsius = -50D;
            normalized = true;
        }
        if (rh != null && rh > 100) {
            rh = 100;
            normalized = true;
        }
        if (rh != null && rh < 0) {
            rh = 0;
            normalized = true;
        }
        return normalized;
    }


}
