package com.alexsoft.smarthouse.watchdog.internal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchdog_job", schema = "main")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchdogJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "state_url", length = 512)
    private String stateUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
