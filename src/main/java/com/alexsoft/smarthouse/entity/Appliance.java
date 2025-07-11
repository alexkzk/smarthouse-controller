package com.alexsoft.smarthouse.entity;

import com.alexsoft.smarthouse.utils.MapToJsonConverter;
import com.alexsoft.smarthouse.utils.StringListConverter;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Entity
@Table(schema = "main")
@ToString
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

    private LocalDateTime lockedUntil;

    private Double setting;
    private Double actual;

    private Double hysteresis;

    @Convert(converter = StringListConverter.class)
    private List<String> referenceSensors;
    private LocalDateTime switched;
    private LocalDateTime switchedOn;
    private LocalDateTime switchedOff;

    private Double durationOnMinutes;

    private Double durationOffMinutes;

    @Version
    private Integer version;

    @Convert(converter = MapToJsonConverter.class)
    @Column(length = 2048)
    private Map<String, String> displayStatus;

    @Deprecated
    public void setState(ApplianceState state) {
        this.state = state;
    }

    public void setState(ApplianceState state, LocalDateTime localdatetime) {
        if (state != this.state) {
            if (state == ON) {
                switched = switchedOn = localdatetime;
                if (switchedOn != null && switchedOff != null) {
                   durationOffMinutes = (double) Duration.between(switchedOff, switchedOn).toMinutes();
                }
            } else if (state == OFF) {
                switched = switchedOff = localdatetime;
                if (switchedOn != null && switchedOff != null) {
                    durationOnMinutes = (double) Duration.between(switchedOn, switchedOff).toMinutes();
                }
            }
            this.state = state;
        }
    }

    @JsonIgnore
    public String getFormattedState() {
        String color = getState() == OFF ? "31" : "32";
        return "\u001B[" + color + "m%s\u001B[0m".formatted(getState());
    }
}
