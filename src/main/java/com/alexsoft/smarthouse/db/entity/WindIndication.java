package com.alexsoft.smarthouse.db.entity;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main")
public class WindIndication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wind_indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "wind_indication_sq", name = "wind_indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Enumerated(EnumType.STRING)
    MeasurePlace measurePlace;

    Integer direction;

    Integer speed;

    @ManyToOne
    private HouseState houseState;

}
