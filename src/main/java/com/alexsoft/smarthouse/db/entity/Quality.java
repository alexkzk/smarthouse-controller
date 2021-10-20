package com.alexsoft.smarthouse.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Objects;

import static com.alexsoft.smarthouse.utils.MathUtils.isNullOrNan;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "air_quality_indication", schema = "main")
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

    @OneToOne(cascade = CascadeType.ALL)
    @JsonProperty("meta")
    private Bme680Meta bme680Meta;

    @JsonIgnore
    public boolean isEmpty() {
        return (iaq == null || iaq < 1) && isNullOrNan(pm25) && isNullOrNan(pm10);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quality quality = (Quality) o;
        return Objects.equals(id, quality.id) && Objects.equals(iaq, quality.iaq) && Objects.equals(pm25, quality.pm25) && Objects.equals(pm10, quality.pm10) && Objects.equals(bme680Meta, quality.bme680Meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, iaq, pm25, pm10, bme680Meta);
    }
}
