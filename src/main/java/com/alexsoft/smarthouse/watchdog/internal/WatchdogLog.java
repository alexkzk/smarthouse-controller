package com.alexsoft.smarthouse.watchdog.internal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchdog_log", schema = "main")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchdogLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private WatchdogJob job;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();

    @Column(name = "gathered_state", columnDefinition = "TEXT")
    private String gatheredState;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(nullable = false)
    private String status;

    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        executedAt = LocalDateTime.now();
    }
}
