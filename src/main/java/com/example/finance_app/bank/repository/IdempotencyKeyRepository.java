package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    // Key is stored as "{userId}:{clientKey}" to scope it per user
    Optional<IdempotencyKey> findByKey(String key);

    // Used by a scheduled cleanup job to purge expired keys (TTL = 24h)
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
