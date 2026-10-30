package uk.co.stefirby.budgetsandsubscriptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.stefirby.budgetsandsubscriptions.model.Transaction;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
