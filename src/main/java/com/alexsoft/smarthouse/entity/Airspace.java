package com.alexsoft.smarthouse.entity;

import com.alexsoft.smarthouse.utils.MapToJsonConverter;
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

    private Integer hourFrom;
    private Integer hourTo;

    @Column(columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, String> params;

}
