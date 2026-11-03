package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService

import java.time.Clock
import java.time.Instant

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAndPersistAccount1
import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionEntityHelper.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser
import static uk.co.stefirby.budgetsandsubscriptions.model.util.UserEntityHelper.createUser2

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class TransactionServiceDataJpaSpec extends Specification {

    @Subject
    TransactionService transactionService

    @Autowired
    TransactionRepository transactionRepository
    @Autowired
    AccountRepository accountRepository
    @Autowired
    UserRepository userRepository

    AccountAccessService accountAccessService

    def setup() {
        accountAccessService = new AccountAccessService(accountRepository)
        transactionService = new TransactionService(accountAccessService, accountRepository, transactionRepository)
    }

    def "BACKEND-003-AC-06: creates a transaction and increases the account's balance"() {
        given: "an authenticated user and an owned account"
            Instant startTest = Instant.now(Clock.systemDefaultZone())
            UUID userId = createUser(userRepository)
            UUID userAccount = createAndPersistAccount1(userId, accountRepository)
            BigDecimal originalBalance = accountRepository.findById(userAccount).get().balance

        and: "a valid transaction creation request"
            TransactionCreateRequest createRequest = positiveTransactionCreateRequest()

        when: "addTransaction is called"
            transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "the new transaction is present in the database"
            List<Transaction> transactions = transactionRepository.findAllByAccountId(userAccount)
            transactions.size() == 1
            Transaction createdTransaction = transactions.getLast()
            createdTransaction.createdAt.isAfter(startTest)

        and: "the account's balance is increased by the transaction's amount"
            BigDecimal newBalance = accountRepository.findById(userAccount).get().balance
            newBalance == originalBalance.add(createRequest.amount())
    }

    def "BACKEND-003-AC-07: AccountNotFoundException is thrown for a non-existent account on create"() {
        given: "a non-existent account id"
            UUID userId = createUser(userRepository)
            UUID userAccount = UUID.randomUUID()

        and: "a valid transaction creation request"
            TransactionCreateRequest createRequest = positiveTransactionCreateRequest()

        when: "addTransaction is called"
            transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "exception is thrown"
            thrown(AccountNotFoundException)

        and: "no transaction is created in the DB for that user"
            transactionRepository.findAllByAccountId(userAccount).size() == 0
    }

    def "BACKEND-003-AC-07: AccountNotFoundException s thrown for a non-owned account on create"() {
        given: "an account id owned by a different user"
            UUID userId1 = createUser(userRepository)

            UUID userId2 = createUser2(userRepository)
            UUID user2Account1 = createAndPersistAccount1(userId2, accountRepository)

        and: "a valid transaction creation request"
            TransactionCreateRequest createRequest = positiveTransactionCreateRequest()

        when: "addTransaction is called"
            transactionService.addTransaction(userId1.toString(), user2Account1, createRequest)

        then: "exception is thrown"
            thrown(AccountNotFoundException)

        and: "no transaction is created in the DB for that user"
            transactionRepository.findAllByAccountId(user2Account1).size() == 0
    }

    def "BACKEND-003-AC-10: rejects creation with a too-early date"() {
        given: "an authenticated user and an owned account"
            UUID userId = createUser(userRepository)
            UUID userAccount = createAndPersistAccount1(userId, accountRepository)

        and: "a dated transaction creation request"
            TransactionCreateRequest createRequest = transactionCreateRequestBeforeOpenedOn()

        when: "addTransaction is called"
            transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "exception is thrown"
            thrown(TransactionDateException)

        and: "no transaction is created in the DB for that user"
            transactionRepository.findAllByAccountId(userAccount).size() == 0
    }
}
