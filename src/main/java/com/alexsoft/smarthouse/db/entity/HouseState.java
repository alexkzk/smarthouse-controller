package com.alexsoft.smarthouse.db.entity;


import java.time.LocalDateTime;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main")
public class HouseState {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "house_state_sq")
    @SequenceGenerator(schema = "main", sequenceName = "house_state_sq", name = "house_state_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    /**
     * An MQTT message issue date time
     */
    private LocalDateTime messageIssued;

    /**
     * Actual MQTT message receiving date time
     */
    private LocalDateTime messageReceived;

    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AirQualityIndication> airQualities;

    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HeatIndication> heatIndications;

    public void addTemperature(HeatIndication heatIndication) {
        if (heatIndication != null) {
            heatIndication.setHouseState(this);
            heatIndications.add(heatIndication);
        }
    }

    public void setParentForAll() {
        airQualities.forEach(aqi -> aqi.setHouseState(this));
        heatIndications.forEach(temp -> temp.setHouseState(this));
    }

    public boolean isNull() {
        for (AirQualityIndication airQualityIndication : airQualities) {
            if (!airQualityIndication.isNull()) {
                return false;
            }
        }
        for (HeatIndication heatIndication : heatIndications) {
            if(!heatIndication.isNull()) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends HouseStateBuilder {

        @Override public HouseState build() {
            HouseState houseState = super.build();
            houseState.setParentForAll();
            return houseState;
        }
    }

}
