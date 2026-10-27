package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.AccountResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountConstants.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAccount1
import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAccount2
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser2

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

    def "BACKEND-002-AC-13: lists only the authenticated user's own accounts"() {
        given: "two users, first one has 2 accounts, second has 1"
            UUID uuid1 = createUser(userRepository)
            UUID user1Account1 = createAccount1(uuid1, accountRepository)
            UUID user1Account2 = createAccount2(uuid1, accountRepository)

            UUID uuid2 = createUser2(userRepository)
            UUID user2Account1 = createAccount1(uuid2, accountRepository)

        when: "getAccounts is called for user 1 and user 2"
            List<AccountResponse> user1AccountList = accountService.getAccounts(uuid1.toString())
            List<AccountResponse> user2AccountList = accountService.getAccounts(uuid2.toString())

        then: "user 1 gets their 2 accounts with the correct Ids"
            user1AccountList.size() == 2
            user1AccountList.any {it.id() == user1Account1}
            user1AccountList.any {it.id() == user1Account2}

        and: "user 2 gets one account"
            user2AccountList.size() == 1
            user2AccountList.getFirst().id() == user2Account1
    }

    def "BACKEND-002-AC-14: returns an empty list for a user with no accounts"() {
        given: "a user with no account"
            UUID uuid1 = createUser(userRepository)

        when: "getAccounts is called for that user"
            List<AccountResponse> user1AccountList = accountService.getAccounts(uuid1.toString())

        then: "response is an empty list"
            user1AccountList.size() == 0
    }

    def "BACKEND-002-AC-15: returns a single owned account"() {
        given: "a user, with 2 accounts"
            UUID uuid = createUser(userRepository)
            UUID userAccount1 = createAccount1(uuid, accountRepository)
            UUID userAccount2 = createAccount2(uuid, accountRepository)

        when: "getAccount is called for one of the accounts"
            AccountResponse response = accountService.getAccount(uuid.toString(), userAccount1)

        then: "the response is populated from that account"
            response.id() == userAccount1
            response.id() != userAccount2
    }

    def "BACKEND-002-AC-16: exception is thrown for a non-existent account"() {
        given: "a non-existent account id"
            UUID uuid = createUser(userRepository)
            UUID userAccount = UUID.randomUUID()

        when: "getAccount is called"
            accountService.getAccount(uuid.toString(), userAccount)

        then: "exception is thrown"
            thrown(AccountNotFoundException)
    }

    def "BACKEND-002-AC-16: exception is thrown for a non-owned account"() {
        given: "an account id owned by a different user"
            UUID uuid1 = createUser(userRepository)

            UUID uuid2 = createUser2(userRepository)
            UUID user2Account1 = createAccount1(uuid2, accountRepository)

        when: "getAccount is called on user2's account with user1 as the owner"
            accountService.getAccount(uuid1.toString(), user2Account1)

        then: "exception is thrown"
            thrown(AccountNotFoundException)
    }
}
