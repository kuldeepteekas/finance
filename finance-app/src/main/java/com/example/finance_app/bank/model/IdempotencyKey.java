package com.example.finance_app.bank.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The client-supplied idempotency key (unique per user operation)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String key;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // HTTP status code of the original response (stored for replay)
    @Column(nullable = false)
    private int responseStatus;

    // JSON snapshot of the original response body (replayed on duplicate requests)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Keys expire after 24h; a scheduled cleanup job removes expired records
    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
