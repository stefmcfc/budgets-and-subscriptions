package uk.co.stefirby.budgetsandsubscriptions.model

import jakarta.validation.ConstraintViolationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import java.time.LocalDate

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAndPersistAccount1
import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionEntityHelper.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class TransactionSpec extends Specification {

    @Autowired
    AccountRepository accountRepository

    @Autowired
    UserRepository userRepository

    @Autowired
    TransactionRepository transactionRepository

    private UUID createUserAndAccount() {
        UUID userId = createUser(userRepository)
        return createAndPersistAccount1(userId, accountRepository)
    }

    def "BACKEND-003-AC-01: rejects a null amount"() {
        given: "a new Transaction with a null amount"
            UUID accountId = createUserAndAccount()
            Transaction newTransaction = new Transaction(accountId, null,
                LocalDate.of(2026, 2, 1), "description", "raw description")

        when: "the transaction is persisted"
            transactionRepository.saveAndFlush(newTransaction)

        then: "persistence fails validation"
            thrown(DataIntegrityViolationException)
    }

    def "BACKEND-003-AC-01: accepts both a positive, zero, and a negative amount"() {
        given: "a new Transaction with a positive amount, one with zero, and a separate one with a negative amount"
            UUID accountId = createUserAndAccount()

            Transaction positiveTransaction = positiveTransaction(accountId)
            Transaction negativeTransaction = negativeTransaction(accountId)
            Transaction zeroTransaction = zeroTransaction(accountId)

        when: "each transaction is persisted"
            transactionRepository.saveAndFlush(positiveTransaction)
            transactionRepository.saveAndFlush(negativeTransaction)
            transactionRepository.saveAndFlush(zeroTransaction)

        then: "all persist successfully with their given amount"
            List<Transaction> transactionList = transactionRepository.findAllByAccountId(accountId)
            transactionList.size() == 3
            transactionList.any { it.amount == positiveTransaction.amount }
            transactionList.any { it.amount == negativeTransaction.amount }
            transactionList.any { it.amount == zeroTransaction.amount }

    }

    def "BACKEND-003-AC-02: rejects a null date"() {
        given: "a new Transaction with a null date"
            UUID accountId = createUserAndAccount()
            Transaction newTransaction = new Transaction(accountId, BigDecimal.valueOf(100),
                null, "description", "raw description")

        when: "the transaction is persisted"
            transactionRepository.saveAndFlush(newTransaction)

        then: "persistence fails validation"
            thrown(DataIntegrityViolationException)
    }

    @Unroll
    def "BACKEND-003-AC-03: rejects a #scenario description"() {
        given: "a new Transaction with a null or blank description"
            UUID accountId = createUserAndAccount()
            Transaction newTransaction = new Transaction(accountId, BigDecimal.valueOf(100),
                LocalDate.of(2026, 2, 1), description, "raw description")

        when: "the transaction is persisted"
            transactionRepository.saveAndFlush(newTransaction)

        then: "persistence fails validation"
            thrown(ConstraintViolationException)

        where:
            scenario | description
            "null"   | null
            "empty"  | ""
            "blank"  | "      "
    }

    def "BACKEND-003-AC-03a: accepts a null rawDescription"() {
        given: "a new Transaction with a null rawDescription"
            UUID accountId = createUserAndAccount()
            Transaction newTransaction = new Transaction(accountId, BigDecimal.valueOf(100),
                LocalDate.of(2026, 2, 1), "description", null)

        when: "the transaction is persisted"
            transactionRepository.saveAndFlush(newTransaction)

        then: "persistence succeeds"
            List<Transaction> transactionList = transactionRepository.findAllByAccountId(accountId)
            transactionList.size() == 1
    }

    @Unroll
    def "BACKEND-003-AC-04: rejects persistence with #scenario"() {
        given: "a new Transaction with no Account set"
            Transaction newTransaction = new Transaction(accountId, BigDecimal.valueOf(100),
                LocalDate.of(2026, 2, 1), "description", "raw")

        when: "the transaction is persisted"
            transactionRepository.saveAndFlush(newTransaction)

        then: "persistence fails"
            thrown(DataIntegrityViolationException)

        where:
            scenario                 | accountId
            "null account Id"        | null
            "no owning account set"  | UUID.randomUUID()
    }

    def "BACKEND-003-AC-05: application context loads with a schema-valid transactions table"() {
        expect: "the Spring context has started successfully with a valid TransactionRepository"
            transactionRepository != null
    }
}
