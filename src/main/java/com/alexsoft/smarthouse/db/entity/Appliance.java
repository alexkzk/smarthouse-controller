package com.alexsoft.smarthouse.db.entity;

import com.alexsoft.smarthouse.db.converter.StringListConverter;
import com.alexsoft.smarthouse.enums.ApplianceState;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

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

    private LocalDateTime lockedAt;

    private Double setting;

    private Double hysteresis;

    @Convert(converter = StringListConverter.class)
    private List<String> referenceSensors;
    private LocalDateTime turnedOn;

    private LocalDateTime turnedOff;

    private Double durationOnMinutes;

    private Double durationOffMinutes;

    @Deprecated
    public void setState(ApplianceState state) {
        this.state = state;
    }

    public void setState(ApplianceState state, LocalDateTime localdatetime) {
        if (state != this.state) {
            if (durationOnMinutes != null && durationOffMinutes != null) {
                durationOffMinutes = (double) Duration.between(turnedOff, turnedOn).toMinutes();
            }
            if (state == ON) {
                turnedOn = localdatetime;
            } else if (state == OFF) {
                turnedOff = localdatetime;
            }
        }
        this.state = state;
    }

    public String getFormattedState() {
        String color = getState() == OFF ? "31" : "32";
        return "\u001B[" + color + "m%s\u001B[0m".formatted(getState());
    }
}
