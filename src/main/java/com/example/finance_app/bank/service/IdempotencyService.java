package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.model.IdempotencyKey;
import com.example.finance_app.bank.model.User;
import com.example.finance_app.bank.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // Keys are scoped per user: "{userId}:{clientKey}".
    // Prevents user A from accidentally (or intentionally) matching user B's key.
    private String scopedKey(UUID userId, String rawKey) {
        return userId + ":" + rawKey;
    }

    // Returns the existing record if it exists and hasn't expired (TTL = 24h).
    public Optional<IdempotencyKey> findExistingKey(UUID userId, String rawKey) {
        return idempotencyKeyRepository.findByKey(scopedKey(userId, rawKey))
                .filter(k -> k.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    // Stores the response for a single-transaction operation (deposit, withdraw).
    public void save(UUID userId, String rawKey, int httpStatus, Object responseBody, User user) {
        try {
            String json = objectMapper.writeValueAsString(responseBody);
            IdempotencyKey record = IdempotencyKey.builder()
                    .key(scopedKey(userId, rawKey))
                    .user(user)
                    .responseStatus(httpStatus)
                    .responseBody(json)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            idempotencyKeyRepository.save(record);
        } catch (Exception e) {
            // Idempotency storage failure must not fail the original operation.
            // Worst case: a duplicate request won't be detected — acceptable trade-off.
            log.warn("Failed to store idempotency key={}: {}", rawKey, e.getMessage());
        }
    }

    // Stores the response for exchange (returns two transactions — OUT and IN).
    public void saveList(UUID userId, String rawKey, int httpStatus, List<TransactionResponse> responses, User user) {
        save(userId, rawKey, httpStatus, responses, user);
    }

    // Replays a stored single-transaction response.
    public TransactionResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, TransactionResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize idempotency response", e);
        }
    }

    // Replays a stored exchange response (list of two transactions).
    public List<TransactionResponse> deserializeList(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TransactionResponse.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize idempotency exchange response", e);
        }
    }
}
