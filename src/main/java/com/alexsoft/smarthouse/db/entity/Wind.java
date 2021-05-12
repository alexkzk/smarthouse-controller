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
@Table(schema = "main")
public class Wind {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wind_sq")
    @SequenceGenerator(schema = "main", sequenceName = "wind_sq", name = "wind_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer direction;

    private Integer speedMs;

}
