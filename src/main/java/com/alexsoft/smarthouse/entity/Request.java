package com.alexsoft.smarthouse.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(schema = "main", name = "request")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "request_sq_v3")
    @SequenceGenerator(schema = "main", sequenceName = "request_sq_v3",
            name = "request_sq_v3", allocationSize = 1)
    @Column(nullable = false, updatable = false)
    private Integer id;

    private LocalDateTime utcTime;

    private String requesterId;

}
