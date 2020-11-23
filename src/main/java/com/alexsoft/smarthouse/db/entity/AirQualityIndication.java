package com.alexsoft.smarthouse.db.entity;

import javax.persistence.*;

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

    private static final int PM_ACCURACY = 10;
    private static final int IAQ_ACCURACY = 1;
    private static final int CO2_ACCURACY = 1;
    private static final int VOC_ACCURACY = 100;

    @Builder
    public AirQualityIndication(
        final MeasurePlace measurePlace, final HouseState houseState, final Integer id,
        final Float pm25, final Float pm10, final Float staticIaq, final Float iaq, final Integer iaqAccuracy, final Float gasResistance,
        final Float maxIaq, final Float co2, final Float voc
    ) {
        super(measurePlace, houseState);
        this.id = id;
        this.pm25 = pm25 == null || Float.isNaN(pm25) ? null : (float) Math.round(pm25 * PM_ACCURACY) / PM_ACCURACY;
        this.pm10 = pm10 == null || Float.isNaN(pm10) ? null : (float) Math.round(pm10 * PM_ACCURACY) / PM_ACCURACY;
        this.staticIaq = staticIaq == null || Float.isNaN(staticIaq) ? null : (float) Math.round(staticIaq * IAQ_ACCURACY) / IAQ_ACCURACY;
        this.iaq = iaq == null || Float.isNaN(iaq) ? null : (float) Math.round(iaq * IAQ_ACCURACY) / IAQ_ACCURACY;
        this.gasResistance = gasResistance == null || Float.isNaN(gasResistance) ? null : (float) Math.round(gasResistance * IAQ_ACCURACY) / IAQ_ACCURACY;
        this.iaqAccuracy = iaqAccuracy;
        this.maxIaq = maxIaq == null || Float.isNaN(maxIaq) ? null : (float) Math.round(maxIaq * IAQ_ACCURACY) / IAQ_ACCURACY;
        this.co2 = co2 == null || Float.isNaN(co2) ? null : (float) Math.round(co2 * CO2_ACCURACY) / CO2_ACCURACY;
        this.voc = voc == null || Float.isNaN(voc) ? null : (float) Math.round(voc * VOC_ACCURACY) / VOC_ACCURACY;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_quality_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_quality_indication_sq",
        name = "air_quality_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Float pm25;

    private Float pm10;

    private Float staticIaq;  //  TODO convert to int

    private Integer iaqAccuracy;

    private Float gasResistance;

    private Float iaq;  //  TODO convert to int

    @Transient private Float maxIaq;

    private Float co2;  //  TODO convert to int

    private Float voc;

    @JsonIgnore public boolean isNull() {
        return (pm25 == null || Float.isNaN(pm25)) && (pm10 == null || Float.isNaN(pm10)) && (staticIaq == null || Float.isNaN(staticIaq))
            && (co2 == null || Float.isNaN(co2)) && (voc == null || Float.isNaN(voc));
    }
}
