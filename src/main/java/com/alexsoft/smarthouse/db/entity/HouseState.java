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
@Builder/*(toBuilder = true, builderClassName = "HouseStateBuilder")*/
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

    private LocalDateTime issued;

    private LocalDateTime received;

    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Aqi> aqis;

    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Temperature> temperatures;

    public void addTemperature(Temperature temperature) {
        if (temperature != null) {
            temperature.setHouseState(this);
            temperatures.add(temperature);
        }
    }

    public void setParentForAll() {
        aqis.forEach(aqi -> aqi.setHouseState(this));
        temperatures.forEach(temp -> temp.setHouseState(this));
    }

    public boolean isNull() {
        for (Aqi aqi : aqis) {
            if (!aqi.isNull()) {
                return false;
            }
        }
        for (Temperature temperature : temperatures) {
            if(!temperature.isNull()) {
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
