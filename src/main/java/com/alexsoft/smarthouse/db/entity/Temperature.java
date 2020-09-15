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
@Table(schema = "main", name = "temp")
public class Temperature {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "temp_sq")
    @SequenceGenerator(schema = "main", sequenceName = "temp_sq", name = "temp_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Enumerated(EnumType.STRING)
    private MeasurePlace measurePlace;

    private Double temperature;

    private Double rh;

    private Double ah;

    @ManyToOne
    private HouseState houseState;

}
