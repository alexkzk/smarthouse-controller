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
@Table(name = "air_temp", schema = "main")
public class Temp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_temp_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_temp_sq", name = "air_temp_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Double celsius;

    private Integer rh;

    private Double ah;


}
