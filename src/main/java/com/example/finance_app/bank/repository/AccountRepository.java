package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Fetches all accounts for a user, newest first
    List<Account> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    // Ownership check built into the query — returns empty if account belongs to a different user.
    // Unauthorized access returns 404 (not 403), preventing account ID enumeration.
    Optional<Account> findByIdAndUser_Id(UUID accountId, UUID userId);

    // CONCURRENCY PROTECTION: SELECT ... FOR UPDATE (pessimistic write lock).
    // Translates to: SELECT * FROM accounts WHERE id = ? FOR UPDATE
    // Postgres holds this row lock until the enclosing transaction commits or rolls back.
    // All other transactions trying to update this account row will block until the lock is released.
    //
    // Called inside @Transactional(REPEATABLE_READ) — never call this outside a transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") UUID accountId);
}
