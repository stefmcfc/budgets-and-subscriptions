package uk.co.stefirby.budgetsandsubscriptions.service.common;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountAccessService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountAccessService.class);

    private final AccountRepository accountRepository;

    public AccountAccessService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public @NonNull Account getAccountForUser(UUID userId, UUID accountId) {
        Optional<Account> checkAccount = accountRepository.findByIdAndUserId(accountId, userId);
        if (checkAccount.isEmpty()) {
            LOG.info("No account with id: {} for user {}", accountId, userId);
            throw new AccountNotFoundException("Account not found");
        }
        return checkAccount.get();
    }
}
