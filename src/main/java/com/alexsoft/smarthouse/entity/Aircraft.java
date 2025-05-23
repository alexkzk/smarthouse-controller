package com.alexsoft.smarthouse.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.Objects;

/*@Entity
@Table(schema = "main", name = "aircraft")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor*/
public class Aircraft {

    /*@Id
    private Integer id;

    private String callsign;

    @ManyToOne(fetch = FetchType.LAZY) // Marks the relationship
    @JoinColumn(name = "airspace_activity_id") // Foreign key column
    private AirspaceActivity airspaceActivity;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Aircraft aircraft = (Aircraft) o;
        return Objects.equals(callsign, aircraft.callsign);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(callsign);
    }*/
}
