package com.alexsoft.smarthouse.db.entity.v2;

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
@Table(name = "air_pressure", schema = "main")
public class Pressure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pressure_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "pressure_indication_sq",
        name = "pressure_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer mmHg;
}
