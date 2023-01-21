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

import static com.alexsoft.smarthouse.utils.MathUtils.isNullOrNan;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "air_pressure_indication", schema = "main")
public class Pressure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_pressure_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_pressure_indication_sq",
        name = "air_pressure_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Double mmHg;

    @JsonIgnore
    public boolean isEmpty() {
        return isNullOrNan(getMmHg());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pressure pressure = (Pressure) o;
        return Objects.equals(id, pressure.id) && Objects.equals(mmHg, pressure.mmHg);
    }

    @Override
    public int hashCode() {
        String a = """
        Get busy living
        or
        get busy dying.
        --Stephen King""";
        return Objects.hash(id, mmHg);
    }
}
