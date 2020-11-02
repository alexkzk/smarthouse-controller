package com.alexsoft.smarthouse.db.entity;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class Measure {

    @Enumerated(EnumType.STRING)
    protected MeasurePlace measurePlace;

    @ToString.Exclude
    @JsonIgnore
    @ManyToOne
    protected HouseState houseState;

    @JsonIgnore
    public abstract boolean isNull();

}
