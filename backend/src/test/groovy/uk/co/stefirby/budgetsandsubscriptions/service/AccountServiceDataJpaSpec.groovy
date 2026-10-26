package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class AccountServiceDataJpaSpec extends Specification {

    @Subject
    AccountService accountService

    @Autowired
    AccountRepository accountRepository
    @Autowired
    UserRepository userRepository

    def setup() {
        accountService = new AccountService(accountRepository)
    }

    def "BACKEND-002-AC-07: when account is created then DB fields are set accordingly"() {
        given: "valid user with UUID format"
            UUID uuid = createUser(userRepository)
            String userId = uuid.toString()

        and: "valid accountCreateRequest"
            AccountCreateRequest accountCreateRequest = new AccountCreateRequest(ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                AccountType.CURRENT, ACCOUNT_OPENING_BALANCE, ACCOUNT_OPENED_ON)

        when: "createAccount is called"
            accountService.createAccount(userId, accountCreateRequest)

        then: "the new account is found in the repository"
            List<Account> savedAccountsForUser = accountRepository.findByUserId(uuid)
            savedAccountsForUser.size() == 1
            Account savedAccount = savedAccountsForUser.getFirst()

        and: "the created account's balance equals the given openingBalance"
            savedAccount.balance == ACCOUNT_OPENING_BALANCE

        and: "the created account is owned by the authenticated user"
            savedAccount.userId == uuid
    }
}
