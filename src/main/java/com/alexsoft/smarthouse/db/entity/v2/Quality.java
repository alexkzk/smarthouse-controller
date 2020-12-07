package com.alexsoft.smarthouse.db.entity.v2;

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
@Table(name = "air_quality", schema = "main")
public class Quality {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_quality_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_quality_sq",
        name = "air_quality_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer iaq;

    private Double pm25;

    private Double pm10;

    @OneToOne
    private Bme680Meta bme680Meta;

}
