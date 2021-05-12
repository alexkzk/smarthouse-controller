package com.alexsoft.smarthouse.db.entity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Data;
import lombok.ToString;

@Data
@Entity
@Table(schema = "main")
public class Air {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_sq", name = "air_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @OneToOne(cascade = CascadeType.ALL)
    private Temp temp;

    @OneToOne(cascade = CascadeType.ALL)
    private Quality quality;

    @OneToOne(cascade = CascadeType.ALL)
    private Pressure pressure;

    @OneToOne(cascade = CascadeType.ALL)
    private Wind wind;
}
