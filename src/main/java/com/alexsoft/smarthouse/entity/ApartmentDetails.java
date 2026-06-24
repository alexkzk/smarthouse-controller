package com.alexsoft.smarthouse.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apartment_details", schema = "main")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApartmentDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_prefix", nullable = false)
    private String locationPrefix;

    @Column(name = "address")
    private String address;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;
}
