package uk.co.stefirby.budgetsandsubscriptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.stefirby.budgetsandsubscriptions.model.Account;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserId(UUID userId);

}
