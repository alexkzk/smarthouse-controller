package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Data;
import lombok.ToString;

/**
 * Represents air quality sensors' metadata information that may help to analyze the environmental situation better
 * @author Author
 */
@Data
@Entity
@Table(name = "bme_680_meta", schema = "main")
public class Bme680Meta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bme680_meta_sq")
    @SequenceGenerator(schema = "main", sequenceName = "bme680_meta_sq",
        name = "bme680_meta_sq", allocationSize = 1)
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
