package uk.co.stefirby.budgetsandsubscriptions.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountUpdateRequest;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountAccessService accountAccessService;

    public AccountService(AccountRepository accountRepository,
                          AccountAccessService accountAccessService) {
        this.accountRepository = accountRepository;
        this.accountAccessService = accountAccessService;
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
        Account userAccount = accountAccessService.getAccountForUser(UUID.fromString(userId), accountId);

        return mapAccountToAccountResponse(userAccount);
    }

    @Transactional
    public AccountResponse updateAccount(String userId, UUID accountId, AccountUpdateRequest requestBody) {
        // first make sure that the account belongs to the owner
        Account userAccount = accountAccessService.getAccountForUser(UUID.fromString(userId), accountId);

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
        Account userAccount = accountAccessService.getAccountForUser(UUID.fromString(userId), accountId);

        accountRepository.delete(userAccount);
    }

    //------------------
    // private methods
    //==================

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
