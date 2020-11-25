package com.alexsoft.smarthouse.db.entityv2;

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
@Table(name = "air_quality_meta", schema = "main")
public class AirQualityMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_quality_meta_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_quality_meta_indication_sq",
        name = "air_quality_meta_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer bme680GasResistance;

    private Integer bme680Co2;

    private Double bme680Voc;

    private Integer bme680IaqAccuracy;

    private Integer bme680StaticIaq;

    private Double bme680RawTemp;

    private Integer bme680RawRh;
}
