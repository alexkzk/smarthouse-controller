package com.alexsoft.smarthouse.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

import com.alexsoft.smarthouse.entity.converter.MapJsonConverter;

@Entity
@Table(schema = "main", name = "event")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_sq_v3")
    @SequenceGenerator(schema = "main", sequenceName = "event_sq_v3",
            name = "event_sq_v3", allocationSize = 1)
    @Column(nullable = false, updatable = false)
    private Integer id;

    private LocalDateTime utcTime;

    private String type;

    // Persist as JSON string in a single column using AttributeConverter
    @Convert(converter = MapJsonConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> data;

}
