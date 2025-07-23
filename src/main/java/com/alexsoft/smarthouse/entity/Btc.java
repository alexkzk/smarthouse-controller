package com.alexsoft.smarthouse.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(schema = "main", name = "btc",
        indexes = {
                @Index(name = "idx_timestamp", columnList = "timestamp"),
                @Index(name = "idx_price", columnList = "price")
        }
)
public class Btc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestampUtc;

    private LocalDateTime timestampEt;


    private Double price;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Btc{");
        sb.append("price=").append(price);
        sb.append('}');
        return sb.toString();
    }
}
