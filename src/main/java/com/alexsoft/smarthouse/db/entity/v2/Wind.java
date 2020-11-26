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
@Table(schema = "main")
public class Wind {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wind_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "wind_indication_sq",
        name = "wind_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer direction;

    private Integer speedMs;

}
