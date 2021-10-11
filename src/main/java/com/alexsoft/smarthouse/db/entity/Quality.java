package com.alexsoft.smarthouse.db.entity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
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

    public boolean isEmpty() {
        return getIaq() == null && getPm25() == null && getPm10() == null;
    }

}
