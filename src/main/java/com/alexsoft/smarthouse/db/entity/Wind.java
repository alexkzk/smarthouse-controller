package com.alexsoft.smarthouse.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "air_wind_indication", schema = "main")
public class Wind {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wind_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "wind_indication_sq", name = "wind_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Integer direction;

    private Integer speedMs;

    @JsonIgnore
    public boolean isEmpty() {
        return direction == null && speedMs == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Wind wind = (Wind) o;
        return Objects.equals(id, wind.id) && Objects.equals(direction, wind.direction) && Objects.equals(speedMs, wind.speedMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, direction, speedMs);
    }
}
