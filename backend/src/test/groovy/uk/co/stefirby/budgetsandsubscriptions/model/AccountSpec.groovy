package uk.co.stefirby.budgetsandsubscriptions.model

import jakarta.validation.ConstraintViolationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.ModelConstants.*

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class AccountSpec extends Specification {

    @Autowired
    AccountRepository accountRepository

    @Autowired
    UserRepository userRepository

    private UUID createUser() {
        User user = new User(USER_EMAIL_VALID, USER_PASSWORD, USER_DISPLAY_NAME)
        userRepository.saveAndFlush(user)
        Optional<User> newUser = userRepository.findByEmail(USER_EMAIL_VALID)
        return newUser.get().id
    }

    def "BACKEND-002-AC-01: rejects a null or blank bank name or account name"() {
        given: "a new Account with a null or blank bankName or accountName"
            UUID userId = createUser()

            Account account = new Account(userId, bankName, accountName,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.SAVINGS)

        when: "the account is persisted"
            accountRepository.saveAndFlush(account)

        then: "persistence fails validation"
            thrown(ConstraintViolationException)

        where:
            scenario             | bankName          | accountName
            "null bank name"     | null              | ACCOUNT_ACCOUNT_NAME
            "empty bank name"    | ""                | ACCOUNT_ACCOUNT_NAME
            "blank bank name"    | "     "           | ACCOUNT_ACCOUNT_NAME
            "null account name"  | ACCOUNT_BANK_NAME | null
            "empty account name" | ACCOUNT_BANK_NAME | ""
            "blank account name" | ACCOUNT_BANK_NAME | "     "
    }

    @Unroll
    def "BACKEND-002-AC-02: allows two accounts with #scenario for the same owner"() {
        given: "a persisted account owned by a user with a given bankName and accountName"
            UUID userId = createUser()

            Account account1 = new Account(userId, bankName1, accountName1,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.SAVINGS)
            accountRepository.saveAndFlush(account1)

        when: "a second account with the same bankName and/or accountName and owner is persisted"
            Account account2 = new Account(userId, bankName2, accountName2,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.SAVINGS)
            accountRepository.saveAndFlush(account2)

        then: "persistence succeeds for both accounts"
            List<Account> accountList = accountRepository.findByUserId(userId)
            accountList.size() == 2

        where:
            scenario                                 | bankName1         | bankName2           | accountName1         | accountName2
            "same bank name, same account name"      | ACCOUNT_BANK_NAME | ACCOUNT_BANK_NAME   | ACCOUNT_ACCOUNT_NAME | ACCOUNT_ACCOUNT_NAME
            "same bank name, different account name" | ACCOUNT_BANK_NAME | ACCOUNT_BANK_NAME   | ACCOUNT_ACCOUNT_NAME | ACCOUNT_ACCOUNT_NAME_1
            "different bank name, same account name" | ACCOUNT_BANK_NAME | ACCOUNT_BANK_NAME_1 | ACCOUNT_ACCOUNT_NAME | ACCOUNT_ACCOUNT_NAME
    }

    def "BACKEND-002-AC-03: accepts each of the three v1 account types"() {
        given: "a new Account for each of CURRENT, SAVINGS, and CASH_ISA"
            UUID userId = createUser()
            Account account1 = new Account(userId, ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.CURRENT)
            Account account2 = new Account(userId, ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.SAVINGS)
            Account account3 = new Account(userId, ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.CASH_ISA)

        when: "each account is persisted"
            accountRepository.saveAndFlush(account1)
            accountRepository.saveAndFlush(account2)
            accountRepository.saveAndFlush(account3)

        then: "all three persist successfully with their given type"
            List<Account> accountList = accountRepository.findByUserId(userId)
            accountList.size() == 3
            accountList.any { it.accountType == AccountType.CURRENT }
            accountList.any { it.accountType == AccountType.SAVINGS }
            accountList.any { it.accountType == AccountType.CASH_ISA }
    }

    def "BACKEND-002-AC-04: rejects persistence with no owner set"() {
        given: "a new Account with no owner set"
            Account account = new Account(null, ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                ACCOUNT_OPENED_ON, ACCOUNT_OPENING_BALANCE, AccountType.CURRENT)

        when: "the account is persisted"
            accountRepository.saveAndFlush(account)

        then: "persistence fails"
            thrown(ConstraintViolationException)
    }

    def "BACKEND-002-AC-05: seeds balance equal to opening balance at construction"() {
        given: "a given openingBalance value"
            UUID userId = createUser()
            BigDecimal openingBalance = 5000.01
            Account account = new Account(userId, ACCOUNT_BANK_NAME, ACCOUNT_ACCOUNT_NAME,
                ACCOUNT_OPENED_ON, openingBalance, AccountType.CURRENT)

        when: "a new Account is constructed with that openingBalance"
            accountRepository.saveAndFlush(account)

        then: "the account's balance equals the given openingBalance"
            List<Account> accountList = accountRepository.findByUserId(userId)
            accountList.size() == 1
            accountList.getFirst().balance == openingBalance
    }

    def "BACKEND-002-AC-06: application context loads with a schema-valid accounts table"() {
        expect: "the Spring context has started successfully with a valid AccountRepository"
            accountRepository != null
    }
}
