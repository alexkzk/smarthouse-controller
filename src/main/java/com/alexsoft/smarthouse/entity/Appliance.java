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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private LocalDateTime lockedUntilUtc;

    private Double setting;
    private Double actual;
    private String metricType;
    private String units;

    private Double hysteresis;

    @Convert(converter = StringListConverter.class)
    private List<String> referenceSensors;
    private LocalDateTime switched;
    private LocalDateTime switchedOn;
    private LocalDateTime switchedOff;

    private Double durationOnMinutes;

    private Double durationOffMinutes;

    @Convert(converter = MapToJsonConverter.class)
    @Column(length = 2048)
    private Map<String, Object> schedule;

    public Double getHysteresis() {
        if (schedule != null) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York"));
                int hour = zonedDateTime.getHour();
                String day = zonedDateTime.getDayOfWeek().toString().substring(0, 3);
                Map<String, Object> value = (Map<String, Object>) schedule.get(day);
                if (value == null) {
                    value = (Map<String, Object>) schedule.get("*");
                }
                if (value == null) {
                    return hysteresis;
                } else {
                    Object value2 = value.get(String.valueOf(hour));
                    if (value2 == null) {
                        value2 = value.get("*");
                    }
                    if (value2 == null) {
                        return hysteresis;
                    } else {
                        double v = Double.parseDouble(((String) value2).split("/")[1]);
                        return v;
                    }
                }
            } catch (Exception e) {
                return hysteresis;
            }
        }
        return hysteresis;
    }

    public Double getSetting() {
        if (schedule != null) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York"));
                int hour = zonedDateTime.getHour();
                String day = zonedDateTime.getDayOfWeek().toString().substring(0, 3);
                Map<String, Object> value = (Map<String, Object>) schedule.get(day);
                if (value == null) {
                    value = (Map<String, Object>) schedule.get("*");
                }
                if (value == null) {
                    return setting;
                } else {
                    Object value2 = value.get(String.valueOf(hour));
                    if (value2 == null) {
                        value2 = value.get("*");
                    }
                    if (value2 == null) {
                        return setting;
                    } else {
                        double v = Double.parseDouble(((String) value2).split("/")[0]);
                        return v;
                    }
                }
            } catch (Exception e) {
                return setting;
            }
        }
        return setting;
    }

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
