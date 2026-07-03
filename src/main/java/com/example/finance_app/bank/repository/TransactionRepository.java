package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // First page — no cursor, ordered newest first.
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.account.id = :accountId AND t.user.id = :userId " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Transaction> findFirstPage(
            @Param("accountId") UUID accountId,
            @Param("userId") UUID userId,
            Pageable pageable);

    // Subsequent pages — cursor = (createdAt, id) of the last item seen on the previous page.
    // The compound condition (createdAt < cursor OR (createdAt = cursor AND id < cursorId))
    // correctly handles rows created at the exact same millisecond (uses UUID as tiebreaker).
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.account.id = :accountId AND t.user.id = :userId " +
           "AND (t.createdAt < :cursorDate " +
           "     OR (t.createdAt = :cursorDate AND t.id < :cursorId)) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Transaction> findNextPage(
            @Param("accountId") UUID accountId,
            @Param("userId") UUID userId,
            @Param("cursorDate") LocalDateTime cursorDate,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
