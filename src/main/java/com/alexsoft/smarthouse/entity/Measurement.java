package com.alexsoft.smarthouse.entity;

import lombok.*;
import lombok.experimental.Accessors;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;

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

    private Double roundToTwoDecimals(Double number) {
        if (number == null) {
            return null;
        }
        return BigDecimal.valueOf(number)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public Measurement setValue(Double value) {
        if (value != null && value > -100) {
            this.value = roundToTwoDecimals(value);
        } else {
            this.value = null;
        }
        return this;
    }

    public Measurement setValue(Integer value) {
        if (value != null && value > -100) {
            this.value = roundToTwoDecimals(value.doubleValue());
        } else {
            this.value = null;
        }
        return this;
    }

    public Measurement setMin(Double min) {
        if (min != null && min > -100) {
            this.min = roundToTwoDecimals(min);
        } else {
            this.min = null;
        }
        return this;
    }

    public Measurement setMax(Double max) {
        if (max != null && max > -100) {
            this.max = roundToTwoDecimals(max);
        } else {
            this.max = null;
        }
        return this;
    }
}
