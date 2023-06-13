package com.alexsoft.smarthouse.db.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "visit", schema = "main")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "visit_sq")
    Integer id;
    LocalDateTime time;
    String ipAddress;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Visit visit = (Visit) o;
        return Objects.equals(id, visit.id) && Objects.equals(time, visit.time) && Objects.equals(ipAddress, visit.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, time, ipAddress);
    }
}
