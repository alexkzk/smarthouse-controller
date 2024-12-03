package com.alexsoft.smarthouse.db.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main", name = "indication_v2")
public class IndicationV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indication_sq_v2")
    @SequenceGenerator(schema = "main", sequenceName = "indication_sq_v2",
            name = "indication_sq_v2", allocationSize = 1)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @Column(name = "indication_place", nullable = false, length = 256)
    private String indicationPlace;

    @Column(name = "aggregation_period", nullable = false, length = 256)
    private String aggregationPeriod;

    @Column(name = "utc_time", nullable = false)
    private LocalDateTime utcTime;

    @Column(name = "local_time")
    private LocalDateTime localTime;

    @Column(name = "publisher_id", nullable = false, length = 256)
    private String publisherId;

    @Column(name = "in_out", nullable = false, length = 16)
    private String inOut;

    @Column(name = "temp_celsius")
    private Double tempCelsius;

    @Column(name = "relative_humidity")
    private Integer relativeHumidity;

    @Column(name = "pressure_mm_hg")
    private Double pressureMmHg;

    @Column(name = "metar", length = 512)
    private String metar;
}
