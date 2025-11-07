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
import java.util.Optional;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.utils.Constants.APPLICATION_OPERATION_TIMEZONE;

@Entity
@Table(schema = "main")
@ToString
@Getter
@Setter
public class Appliance {

    @Id
    private String code;
    private String description;
    @ManyToOne
    @JoinColumn(name = "appliance_group_id")
    private ApplianceGroup applianceGroup;
    @Enumerated(EnumType.STRING)
    private ApplianceState state;
    private Double consumptionKwh;
    private boolean locked = false;
    private LocalDateTime lockedUntilUtc;
    private Double setting;
    private Double scheduledSetting;
    private Double actual;
    private String metricType;
    private String units;
    private Double hysteresis;
    @Convert(converter = StringListConverter.class)
    private List<String> referenceSensors;
    private String measurementType;
    private Integer averagePeriodMinutes;
    private Boolean inverted;
    private Integer minimumOnCycleMinutes;
    private Integer minimumOffCycleMinutes;
    private String zigbee2MqttTopic;
    private LocalDateTime switched;
    private LocalDateTime switchedOn;
    private LocalDateTime switchedOff;
    private Double durationOnMinutes;
    private Double durationOffMinutes;
    private Boolean sensorControlled;
    private Boolean dimmable;
    private Double powerSetting;
    @Convert(converter = MapToJsonConverter.class)
    @Column(length = 2048)
    private Map<String, Object> schedule;

    public Optional<ApplianceGroup> getApplianceGroup() {
        return applianceGroup == null ? Optional.empty() : Optional.of(applianceGroup);
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

    public Double getHysteresis() {
        return getScheduledOrCurrentSetting(1, hysteresis);
    }

    public Double determineScheduledSetting() {
        return getScheduledOrCurrentSetting(0, null);
    }

    private Double getScheduledOrCurrentSetting(int settingIndex, Double defaultValue) {
        if (schedule != null) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of(APPLICATION_OPERATION_TIMEZONE));
                String day = zonedDateTime.getDayOfWeek().toString().substring(0, 3);
                int hour = zonedDateTime.getHour();

                Map<String, Object> daySchedule = getDailySchedule(day);
                if (daySchedule == null) {
                    return defaultValue;
                }

                String value = getDayAndHourSetting(daySchedule, hour);
                if (value == null) {
                    return defaultValue;
                }

                return Double.parseDouble(value.split("/")[settingIndex]);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDailySchedule(String day) {
        Map<String, Object> daySchedule = (Map<String, Object>) schedule.get(day);
        if (daySchedule == null) {
            daySchedule = (Map<String, Object>) schedule.get("*");
        }
        return daySchedule;
    }

    private String getDayAndHourSetting(Map<String, Object> daySchedule, int hour) {
        Object value = daySchedule.get(String.valueOf(hour));
        if (value == null) {
            value = daySchedule.get("*");
        }
        return value != null ? value.toString() : null;
    }

    @Convert(converter = MapToJsonConverter.class)
    @Column(length = 2048)
    private Map<String, String> displayStatus;
}
