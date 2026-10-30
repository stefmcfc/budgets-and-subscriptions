package uk.co.stefirby.budgetsandsubscriptions.service;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountUpdateRequest;
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

    @Transactional
    public List<AccountResponse> getAccounts(String userId) {
        List<Account> accountList = accountRepository.findByUserId(UUID.fromString(userId));
        return mapAccountsToAccountResponses(accountList);
    }

    @Transactional
    public AccountResponse getAccount(String userId, UUID accountId) {
        Account userAccount = getAccountForUser(UUID.fromString(userId), accountId);

        return mapAccountToAccountResponse(userAccount);
    }

    @Transactional
    public AccountResponse updateAccount(String userId, UUID accountId, AccountUpdateRequest requestBody) {
        // first make sure that the account belongs to the owner
        Account userAccount = getAccountForUser(UUID.fromString(userId), accountId);

        // now update the fields as expected
        if (requestBody.accountName() != null && !requestBody.accountName().isEmpty()) {
            userAccount.setAccountName(requestBody.accountName());
        }
        if (requestBody.bankName() != null && !requestBody.bankName().isEmpty()) {
            userAccount.setBankName(requestBody.bankName());
        }
        if (requestBody.type() != null) {
            userAccount.setAccountType(requestBody.type());
        }

        return mapAccountToAccountResponse(accountRepository.saveAndFlush(userAccount));
    }

    @Transactional
    public void deleteAccount(String userId, UUID accountId) {
        // first make sure that the account belongs to the owner
        Account userAccount = getAccountForUser(UUID.fromString(userId), accountId);

        accountRepository.delete(userAccount);
    }

    //------------------
    // private methods
    //==================

    private @NonNull Account getAccountForUser(UUID userId, UUID accountId) {
        Optional<Account> checkAccount = accountRepository.findByIdAndUserId(accountId, userId);
        if (checkAccount.isEmpty()) {
            LOG.info("No account with id: {} for user {}", accountId, userId);
            throw new AccountNotFoundException("Account not found");
        }
        return checkAccount.get();
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
