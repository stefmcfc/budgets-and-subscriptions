package uk.co.stefirby.budgetsandsubscriptions.service

import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse
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

}
