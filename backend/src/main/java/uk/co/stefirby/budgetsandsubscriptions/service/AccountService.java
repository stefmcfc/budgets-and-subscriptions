package uk.co.stefirby.budgetsandsubscriptions.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse;
import uk.co.stefirby.budgetsandsubscriptions.model.Account;
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository;

import java.util.UUID;

@Service
public class AccountService {
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

        return new AccountResponse(
            createdAccount.getId(),
            createdAccount.getBankName(),
            createdAccount.getAccountName(),
            createdAccount.getAccountType(),
            createdAccount.getOpeningBalance(),
            createdAccount.getOpenedOn(),
            createdAccount.getBalance()
        );
    }

}
