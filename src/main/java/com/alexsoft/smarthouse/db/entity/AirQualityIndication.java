package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@Table(schema = "main")
public class AirQualityIndication extends Measure {

    @Builder
    public AirQualityIndication(
        final MeasurePlace measurePlace, final HouseState houseState, final Integer id,
        final Float pm25, final Float pm10, final Float iaq, final Float co2, final Float voc
    ) {
        super(measurePlace, houseState);
        this.id = id;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.iaq = iaq;
        this.co2 = co2;
        this.voc = voc;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_quality_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_quality_indication_sq",
        name = "air_quality_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Float pm25;

    private Float pm10;

    private Float iaq;

    private Float co2;

    private Float voc;

    @JsonIgnore
    public boolean isNull() {
        return (pm25 == null || Float.isNaN(pm25)) && (pm10 == null || Float.isNaN(pm10)) && (iaq == null || Float.isNaN(iaq))
            && (co2 == null || Float.isNaN(co2)) && (voc == null || Float.isNaN(voc));
    }
}
