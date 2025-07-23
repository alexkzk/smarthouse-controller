package com.alexsoft.smarthouse.entity;

import lombok.*;
import lombok.experimental.Accessors;

import jakarta.persistence.Embeddable;

@Embeddable
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Measurement {
    private Double value;
    private Double min;
    private Double max;

    public Measurement(Integer value, Double min, Double max) {
        this.value = value != null ? value.doubleValue() : null;
        this.min = min;
        this.max = max;
    }

    public Measurement setValue(Double value) {
        if (value != null && value > -100) {
            this.value = Math.round(value * 10.0) / 10.0;
        } else {
            this.value = null;
        }
        return this;
    }

    public Measurement setValue(Integer value) {
        if (value != null && value > -100) {
            this.value = Math.round(value * 10.0) / 10.0;
        } else {
            this.value = null;
        }
        return this;
    }

    public Measurement setMin(Double min) {
        if (min != null && min > -100) {
            this.min = Math.round(min * 10.0) / 10.0;
        } else {
            this.min = null;
        }
        return this;
    }

    public Measurement setMax(Double max) {
        if (max != null && max > -100) {
            this.max = Math.round(max * 10.0) / 10.0;
        } else {
            this.max = null;
        }
        return this;
    }


}
