package com.alexsoft.smarthouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "global_settings", schema = "main")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSetting {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", length = 2048)
    private String value;
}
