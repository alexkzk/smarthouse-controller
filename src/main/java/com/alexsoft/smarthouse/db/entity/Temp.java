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
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "air_temp_indication", schema = "main")
public class Temp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_temp_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_temp_indication_sq", name = "air_temp_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    private Double celsius;

    private Integer rh;

    private Double ah;

    @JsonIgnore
    public boolean isEmpty() {
        return isNullOrNan(getCelsius()) && getRh() == null && isNullOrNan(getAh());
    }

    public boolean normalize() {
        boolean normalized = false;
        if (celsius != null && celsius > 50D) {
            celsius = null;
            normalized = true;
        }
        if (celsius != null && celsius < -50D) {
            celsius = null;
            normalized = true;
        }
        if (rh != null && rh > 100) {
            rh = null;
            normalized = true;
        }
        if (rh != null && rh < 0) {
            rh = null;
            normalized = true;
        }
        if (ah != null && ah > 30) {
            ah = null;
            normalized = true;
        }
        if (ah != null && ah < 0) {
            ah = null;
            normalized = true;
        }
        return normalized;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Temp temp = (Temp) o;
        return Objects.equals(id, temp.id) && Objects.equals(celsius, temp.celsius) && Objects.equals(rh, temp.rh) && Objects.equals(ah, temp.ah);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, celsius, rh, ah);
    }
}
