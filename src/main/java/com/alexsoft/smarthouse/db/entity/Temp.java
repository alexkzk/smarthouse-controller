package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
            celsius = null;
            normalized = true;
        }
        if (celsius != null && celsius < -50D) {
            celsius = null;
            normalized = true;
        }
        if (rh != null && rh > 100) {
            rh = null;
            normalized = true;
        }
        if (rh != null && rh < 0) {
            rh = null;
            normalized = true;
        }
        if (ah != null && ah > 30) {
            ah = null;
            normalized = true;
        }
        if (ah != null && ah < 0) {
            ah = null;
            normalized = true;
        }
        return normalized;
    }


}
