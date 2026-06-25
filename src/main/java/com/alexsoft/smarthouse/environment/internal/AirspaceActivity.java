package com.alexsoft.smarthouse.environment.internal;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(schema = "main", name = "airspace_activity")
public class AirspaceActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String airspace;

    @Column(nullable = false, length = 2056)
    private Integer airborneAircrafts;

    private String aircrafts;

    /*@OneToMany(mappedBy = "airspaceActivity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Aircraft> aircraftList = new HashSet<>();*/
}
