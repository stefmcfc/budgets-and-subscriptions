package uk.co.stefirby.budgetsandsubscriptions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse;
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(String userId, AccountCreateRequest accountCreateRequest) {
        Account account = new Account(
            UUID.fromString(userId),
            accountCreateRequest.bankName(),
            accountCreateRequest.accountName(),
            accountCreateRequest.openedOn(),
            accountCreateRequest.openingBalance(),
            accountCreateRequest.type());

        Account createdAccount = accountRepository.saveAndFlush(account);

        return mapAccountToAccountResponse(createdAccount);
    }

    public List<AccountResponse> getAccounts(String userId) {
        List<Account> accountList = accountRepository.findByUserId(UUID.fromString(userId));
        return mapAccountsToAccountResponses(accountList);
    }

    public AccountResponse getAccount(String userId, UUID accountId) {
        AccountResponse accountResponse = null;
        Optional<Account> checkAccount = accountRepository.findByIdAndUserId(accountId, UUID.fromString(userId));
        if (checkAccount.isPresent()) {
            accountResponse = mapAccountToAccountResponse(checkAccount.get());
        } else {
            LOG.info("No account with id: {} ", accountId);
            throw new AccountNotFoundException("Account not found");
        }

        return accountResponse;
    }

    private List<AccountResponse> mapAccountsToAccountResponses(List<Account> accountList) {
        return accountList.stream()
            .map(this::mapAccountToAccountResponse)
            .toList();
    }

    private AccountResponse mapAccountToAccountResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getBankName(),
            account.getAccountName(),
            account.getAccountType(),
            account.getOpeningBalance(),
            account.getOpenedOn(),
            account.getBalance()
        );
    }
}
