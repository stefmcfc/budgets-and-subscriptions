package uk.co.stefirby.budgetsandsubscriptions.model.util

import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*

class AccountEntityHelper {

    static UUID createAccount1(UUID userId, AccountRepository accountRepository) {
        Account account = new Account(
            userId ,ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME, ACCOUNT_OPENED_ON,
            ACCOUNT_OPENING_BALANCE, AccountType.CURRENT
        )
        Account createdAccount = accountRepository.saveAndFlush(account)
        return createdAccount.id
    }

    static UUID createAccount2(UUID userId, AccountRepository accountRepository) {
        Account account = new Account(
            userId ,ACCOUNT_BANK_NAME_1, ACCOUNT_ACCOUNT_NAME_1, ACCOUNT_OPENED_ON_1,
            ACCOUNT_OPENING_BALANCE_1, AccountType.SAVINGS
        )
        Account createdAccount = accountRepository.saveAndFlush(account)
        return createdAccount.id
    }
}
