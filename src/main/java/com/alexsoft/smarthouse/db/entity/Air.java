package com.alexsoft.smarthouse.db.entity;

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

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(schema = "main")
public class Air {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "air_sq")
    @SequenceGenerator(schema = "main", sequenceName = "air_sq", name = "air_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @OneToOne(cascade = CascadeType.ALL)
    private Temp temp;

    @OneToOne(cascade = CascadeType.ALL)
    private Quality quality;

    @OneToOne(cascade = CascadeType.ALL)
    private Pressure pressure;

    @OneToOne(cascade = CascadeType.ALL)
    private Wind wind;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Air air = (Air) o;
        return Objects.equals(id, air.id) && Objects.equals(temp, air.temp) && Objects.equals(quality, air.quality) && Objects.equals(pressure, air.pressure) && Objects.equals(wind, air.wind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, temp, quality, pressure, wind);
    }
}
