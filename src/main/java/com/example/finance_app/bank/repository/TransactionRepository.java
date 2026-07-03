package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Step 5 will extend this with cursor-based pagination queries
}
