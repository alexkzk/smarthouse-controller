package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@Table(schema = "main")
public class WindIndication extends Measure {

    @Builder
    public WindIndication(
        final MeasurePlace measurePlace, final HouseState houseState, final Integer id,
        final Integer direction, final Integer speed
    ) {
        super(measurePlace, houseState);
        this.id = id;
        this.direction = direction;
        this.speed = speed;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wind_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "wind_indication_sq", name = "wind_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    Integer direction;

    Integer speed;

    @Override
    public boolean isNull() {
        return speed == null && direction == null;
    }
}
