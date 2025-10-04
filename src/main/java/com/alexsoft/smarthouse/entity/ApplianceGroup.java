package com.alexsoft.smarthouse.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(schema = "main")
@ToString
@Getter
@Setter
public class ApplianceGroup {

    @Id
    private Integer id;
    private String code;
    private String description;
    private String turnOnHours;
    private String turnOffHours;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApplianceGroup that = (ApplianceGroup) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
