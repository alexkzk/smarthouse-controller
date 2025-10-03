package com.alexsoft.smarthouse.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "main")
@ToString
@Getter
@Setter
public class ApplianceGroup {

    @Id
    private Integer id;
    private String code;
    private String description;

}
