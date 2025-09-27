package com.alexsoft.smarthouse.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(schema = "main", name = "indication_v3",
        indexes = {
                @Index(name = "idx_indication_utc_time", columnList = "utcTime"),
                @Index(name = "idx_indication_local_time", columnList = "localTime"),
                @Index(name = "idx_indication_measurement_type", columnList = "measurementType"),
                @Index(name = "idx_indication_device_id", columnList = "deviceId"),
                @Index(name = "idx_indication_device_type", columnList = "deviceType"),
                @Index(name = "idx_indication_unit", columnList = "unit"),
                @Index(name = "idx_indication_value", columnList = "value")
        }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class IndicationV3 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indication_sq_v3")
    @SequenceGenerator(schema = "main", sequenceName = "indication_sq_v3",
            name = "indication_sq_v3", allocationSize = 1)
    @Column(nullable = false, updatable = false)
    private Integer id;

    private LocalDateTime utcTime;

    private LocalDateTime localTime;

    private String measurementType;

    private String deviceId;

    private String deviceType;

    private String unit;

    private Double value;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IndicationV3 that = (IndicationV3) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
