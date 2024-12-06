package com.alexsoft.smarthouse.db.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main", name = "indication_v2")
public class IndicationV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indication_sq_v2")
    @SequenceGenerator(schema = "main", sequenceName = "indication_sq_v2",
            name = "indication_sq_v2", allocationSize = 1)
    @Column(nullable = false, updatable = false)
    private Integer id;

    @Column(nullable = false, length = 256)
    private String indicationPlace;

    @Column(nullable = false, length = 256)
    private String aggregationPeriod;

    private LocalDateTime utcTime;

    private LocalDateTime localTime;

    private LocalDateTime aggregationTimeUtc;

    private Integer aggregationAccuracy;

    @Column(length = 256)
    private String publisherId;

    @Column(nullable = false, length = 16)
    private String inOut;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "temp")),
            @AttributeOverride(name = "min", column = @Column(name = "temp_min")),
            @AttributeOverride(name = "max", column = @Column(name = "temp_max"))
    })
    private Measurement temperature = new Measurement();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "rh")),
            @AttributeOverride(name = "min", column = @Column(name = "rh_min")),
            @AttributeOverride(name = "max", column = @Column(name = "rh_max"))
    })
    private Measurement relativeHumidity = new Measurement();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "ah")),
            @AttributeOverride(name = "min", column = @Column(name = "ah_min")),
            @AttributeOverride(name = "max", column = @Column(name = "ah_max"))
    })
    private Measurement absoluteHumidity = new Measurement();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "mmhg")),
            @AttributeOverride(name = "min", column = @Column(name = "mmhg_min")),
            @AttributeOverride(name = "max", column = @Column(name = "mmhg_max"))
    })
    private Measurement pressure = new Measurement();

    @Column(length = 512)
    private String metar;

    public Measurement getTemperature() {
        if (temperature == null) {
            temperature = new Measurement();
        }
        return temperature;
    }

    public void setTemperature(Measurement temperature) {
        this.temperature = (temperature != null) ? temperature : new Measurement();
    }

    public Measurement getRelativeHumidity() {
        if (relativeHumidity == null) {
            relativeHumidity = new Measurement();
        }
        return relativeHumidity;
    }

    public void setRelativeHumidity(Measurement relativeHumidity) {
        this.relativeHumidity = (relativeHumidity != null) ? relativeHumidity : new Measurement();
    }

    public Measurement getAbsoluteHumidity() {
        if (absoluteHumidity == null) {
            absoluteHumidity = new Measurement();
        }
        return absoluteHumidity;
    }

    public void setAbsoluteHumidity(Measurement absoluteHumidity) {
        this.absoluteHumidity = (absoluteHumidity != null) ? absoluteHumidity : new Measurement();
    }

    public Measurement getPressure() {
        if (pressure == null) {
            pressure = new Measurement();
        }
        return pressure;
    }

    public void setPressure(Measurement pressure) {
        this.pressure = (pressure != null) ? pressure : new Measurement();
    }

    public IndicationV2 setAggregationAccuracy(Integer aggregationAccuracy) {
        if (aggregationAccuracy != null) {
            this.aggregationAccuracy = aggregationAccuracy > 100 ? 100 : aggregationAccuracy;
        }
        return this;
    }
}
