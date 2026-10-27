package uk.co.stefirby.budgetsandsubscriptions.service

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountUpdateRequest
import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*

class AccountServiceSpec extends Specification {

    @Subject
    AccountService accountService

    AccountRepository mockAccountRepository = Mock()
    Account mockAccount = Mock()

    def setup() {
        accountService = new AccountService(mockAccountRepository)
    }

    def mockCreateAccountResponse(UUID userId, UUID randomId, AccountCreateRequest request, Account mockAccount) {
        mockAccount.id >> randomId
        mockAccount.accountName >> request.accountName()
        mockAccount.accountType >> request.type()
        mockAccount.balance >> request.openingBalance()
        mockAccount.bankName >> request.bankName()
        mockAccount.openedOn >> request.openedOn()
        mockAccount.openingBalance >> request.openingBalance()
        mockAccount.userId >> userId
    }

    def Account buildAccount1(UUID userId) {
        return new Account(
            userId,
            ACCOUNT_BANK_NAME,
            ACCOUNT_ACCOUNT_NAME,
            ACCOUNT_OPENED_ON,
            ACCOUNT_OPENING_BALANCE,
            AccountType.CURRENT
        )
    }
    def Account buildAccount2(UUID userId) {
        return new Account(
            userId,
            ACCOUNT_BANK_NAME_1,
            ACCOUNT_ACCOUNT_NAME_1,
            ACCOUNT_OPENED_ON,
            ACCOUNT_OPENING_BALANCE,
            AccountType.CURRENT
        )
    }

    def "BACKEND-002-AC-07: given user and valid accountCreateRequest then createAccount creates account"() {
        given: "valid user with UUID format"
            UUID uuid = UUID.randomUUID()
            UUID randomId = UUID.randomUUID()
            String userId = uuid.toString()

        and: "valid accountCreateRequest"
            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

        and: "repository returns correct data"
            mockCreateAccountResponse(uuid, randomId, accountCreateRequest, mockAccount)

        when: "createAccount is called"
            AccountResponse response = accountService.createAccount(userId, accountCreateRequest)

        then: "repository saveAndFlush is called once"
            1 * mockAccountRepository.saveAndFlush(_ as Account) >> mockAccount

        and: "response contains fields as expected"
            response.id() == randomId

        and: "the created account's balance equals the given openingBalance"
            response.balance() == ACCOUNT_OPENING_BALANCE
    }

    def "BACKEND-002-AC-13: lists only the authenticated user's own accounts"() {
        given: "valid user with uuid format who has 2 accounts"
            UUID uuid = UUID.randomUUID()
            String userId = uuid.toString()

        when: "getAccounts is called for that user"
            List<AccountResponse> response = accountService.getAccounts(userId)

        then: "findByUserId is called for that account"
            1 * mockAccountRepository.findByUserId(uuid) >> List.of(buildAccount1(uuid), buildAccount2(uuid))

        and: "response returns 2 accounts"
            response.size() == 2
    }

    def "BACKEND-002-AC-14: returns an empty list for a user with no accounts"() {
        given: "valid user with uuid format who has no accounts"
            UUID uuid = UUID.randomUUID()
            String userId = uuid.toString()

        when: "getAccounts is called for that user"
            List<AccountResponse> response = accountService.getAccounts(userId)

        then: "findByUserId is called for that account"
            1 * mockAccountRepository.findByUserId(uuid) >> List.of()

        and: "response returns no accounts"
            response.size() == 0
    }

    def "BACKEND-002-AC-15: returns a single owned account"() {
        given: "valid user with uuid format who has an account"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            mockAccountRepository.findByIdAndUserId(accountId, userId) >> Optional.of(buildAccount1(userId))

        when: "getAccounts is called for that user and account id"
            AccountResponse response = accountService.getAccount(userId.toString(), accountId)

        then: "response is returned"
            response.accountName() == ACCOUNT_ACCOUNT_NAME
    }

    @Unroll
    def "BACKEND-002-AC-17: updates an owned account's bank name, account name, or type - #scenario"() {
        given: "valid user with uuid format who has an account"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()

        and: "a valid AccountUpdateRequest is provided"
            AccountUpdateRequest accountUpdateRequest = new AccountUpdateRequest(
                newBankName, newAccountName, newAccountType)

        when: "updateAccount is called is for the account"
            AccountResponse response = accountService.updateAccount(userId.toString(), accountId, accountUpdateRequest)

        then: "find and update account performed on repository"
            1 * mockAccountRepository.findByIdAndUserId(accountId, userId) >> Optional.of(buildAccount1(userId))
            1 * mockAccountRepository.saveAndFlush(_ as Account) >> { Account a -> a }

        and: "response is returned with updated fields for those in the request and unchanged for the others"
            newBankName != null ? response.bankName() == newBankName : response.bankName() == ACCOUNT_BANK_NAME
            newAccountName != null ? response.accountName() == newAccountName : response.accountName() == ACCOUNT_ACCOUNT_NAME
            newAccountType != null ? response.type() == newAccountType : response.type() == AccountType.CURRENT

        where:
            scenario           | newBankName     | newAccountName     | newAccountType
            "bankName only"    | "New Bank Name" | null               | null
            "accountName only" | null            | "New Account Name" | null
            "accountType only" | null            | null               | AccountType.CASH_ISA
            "all 3 fields"     | "New Bank Name" | "New Account Name" | AccountType.CASH_ISA
    }
}
