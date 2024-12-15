package com.alexsoft.smarthouse.db.entity;

import com.alexsoft.smarthouse.enums.ApplianceState;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(schema = "main")
@Getter
@Setter
public class Appliance {
    @Id
    private String code;
    private String description;
    @Enumerated(EnumType.STRING)
    private ApplianceState state;
    private Double consumptionKwh;
    private boolean locked = false;
    private Double setting;
    private Double hysteresis;
    private LocalDateTime statusUpdated;
    private LocalDateTime lockedAt;

    @Deprecated
    public void setState(ApplianceState state) {
        this.state = state;
    }

    public void setState(ApplianceState state, LocalDateTime localdatetime) {
        if (state != this.state) {
            statusUpdated = localdatetime;
        }
        this.state = state;
    }
}
