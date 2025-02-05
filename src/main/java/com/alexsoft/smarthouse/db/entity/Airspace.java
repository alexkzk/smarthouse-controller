package com.alexsoft.smarthouse.db.entity;

import com.alexsoft.smarthouse.converter.MapToJsonConverter;
import lombok.*;

import jakarta.persistence.*;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(schema = "main")
public class Airspace {

    @Id
    private Integer id;

    private String name;

    @Column(columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, String> params;

}
