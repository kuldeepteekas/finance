package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Fetches all accounts for a user, newest first
    List<Account> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    // Ownership check built into the query — returns empty if account belongs to a different user
    // This means unauthorized access returns 404 (not 403), preventing account ID enumeration
    Optional<Account> findByIdAndUser_Id(UUID accountId, UUID userId);
}
