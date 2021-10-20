package com.alexsoft.smarthouse.db.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Objects;

/**
 * Represents air quality sensors' metadata information that may help to analyze the environmental situation better
 * @author Author
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bme680Meta that = (Bme680Meta) o;
        return Objects.equals(id, that.id) && Objects.equals(bme680GasResistance, that.bme680GasResistance) && Objects.equals(bme680Co2, that.bme680Co2) && Objects.equals(bme680Voc, that.bme680Voc) && Objects.equals(bme680IaqAccuracy, that.bme680IaqAccuracy) && Objects.equals(bme680StaticIaq, that.bme680StaticIaq) && Objects.equals(bme680RawTemp, that.bme680RawTemp) && Objects.equals(bme680RawRh, that.bme680RawRh);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bme680GasResistance, bme680Co2, bme680Voc, bme680IaqAccuracy, bme680StaticIaq, bme680RawTemp, bme680RawRh);
    }
}
