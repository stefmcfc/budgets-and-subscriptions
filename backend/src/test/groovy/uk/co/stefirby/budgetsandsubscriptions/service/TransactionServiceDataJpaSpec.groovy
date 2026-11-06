package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService

import java.time.Clock
import java.time.Instant

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAndPersistAccount1
import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.createAndPersistAccount2
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

    //----------------------------
    // addTransaction tests
    //============================

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

    //----------------------------
    // getAllTransactions tests
    //============================

    def "BACKEND-003-AC-12: lists only the authenticated user's accounts own transactions"() {
        given: "an account owned by the authenticated user with several transactions recorded against it"
            UUID userId = createUser(userRepository)

            UUID userAccount1 = createAndPersistAccount1(userId, accountRepository)
            UUID account1Transaction1 = createAndPersistPositiveTransaction(userAccount1, transactionRepository)
            UUID account1Transaction2 = createAndPersistNegativeTransaction(userAccount1, transactionRepository)
            UUID account1Transaction3 = createAndPersistZeroTransaction(userAccount1, transactionRepository)

            UUID userAccount2 = createAndPersistAccount2(userId, accountRepository)
            UUID account2Transaction1 = createAndPersistPositiveTransaction(userAccount2, transactionRepository)

        when: "getAllTransactions is called for account1 and account2"
            List<TransactionResponse> account1Transactions =
                transactionService.getAllTransactions(userId.toString(), userAccount1)
            List<TransactionResponse> account2Transactions =
                transactionService.getAllTransactions(userId.toString(), userAccount2)

        then: "account1 has three transactions with the correct IDs"
            account1Transactions.size() == 3
            account1Transactions.any {it.id() == account1Transaction1}
            account1Transactions.any {it.id() == account1Transaction2}
            account1Transactions.any {it.id() == account1Transaction3}

        and: "account2 only has one transaction"
            account2Transactions.size() == 1
            account2Transactions.getLast().id() == account2Transaction1
    }

    def "BACKEND-003-AC-13: returns an empty list for an owned account with no transactions"() {
        given: "an account owned by the authenticated user with no transactions"
            UUID userId = createUser(userRepository)
            UUID userAccount = createAndPersistAccount1(userId, accountRepository)

        when: "getAllTransactions is called with that account's id"
            List<TransactionResponse> accountTransactions =
                transactionService.getAllTransactions(userId.toString(), userAccount)

        then: "the response is an empty list"
            accountTransactions.size() == 0
    }

    def "BACKEND-003-AC-14: exception is thrown for a non-existent account"() {
        given: "a non-existent account id"
            UUID userId = createUser(userRepository)
            UUID randomAccount = UUID.randomUUID()

        when: "getAllTransactions is called"
            List<TransactionResponse> accountTransactions =
                transactionService.getAllTransactions(userId.toString(), randomAccount)

        then: "exception is thrown"
            thrown(AccountNotFoundException)
    }

    def "BACKEND-003-AC-14: exception is thrown for a non-owned account"() {
        given: "an account id with transaction(s) owned by a different user"
            UUID user1 = createUser(userRepository)

            UUID user2 = createUser2(userRepository)
            UUID user2Account1 = createAndPersistAccount1(user2, accountRepository)
            createAndPersistPositiveTransaction(user2Account1, transactionRepository)

        when: "getAllTransactions is called on user2's account with user1 as the owner"
            transactionService.getAllTransactions(user1.toString(), user2Account1)

        then: "exception is thrown"
            thrown(AccountNotFoundException)
    }

    //----------------------------
    // getTransaction tests
    //============================

    def "BACKEND-003-AC-15: returns a single owned transaction"() {
        given: "a transaction owned (via its account) by the authenticated user"
            UUID userId = createUser(userRepository)
            UUID userAccount1 = createAndPersistAccount1(userId, accountRepository)
            UUID account1Transaction1 = createAndPersistPositiveTransaction(userAccount1, transactionRepository)
            UUID account1Transaction2 = createAndPersistNegativeTransaction(userAccount1, transactionRepository)

        when: "getTransaction is called for the transactions"
            TransactionResponse transaction1Response =
                transactionService.getTransaction(userId.toString(), userAccount1, account1Transaction1)
            TransactionResponse transaction2Response =
                transactionService.getTransaction(userId.toString(), userAccount1, account1Transaction2)

        then: "the response populated from the correct transaction"
            transaction1Response.id() == account1Transaction1
            transaction2Response.id() == account1Transaction2
    }

    def "BACKEND-003-AC-16: throws AccountNotFoundException for a non-existent accountId"() {
        given: "a valid user"
            UUID userId1 = createUser(userRepository)

        when: "getTransaction called with random accountId"
            transactionService.getTransaction(userId1.toString(), UUID.randomUUID(), UUID.randomUUID())

        then: "AccountNotFoundException thrown"
            thrown(AccountNotFoundException)
    }

    def "BACKEND-003-AC-16: throws AccountNotFoundException for a non-owned accountId"() {
        given: "valid user - user1"
            UUID userId1 = createUser(userRepository)

        and: "valid user with valid account - user2"
            UUID userId2 = createUser2(userRepository)
            UUID user2Account1 = createAndPersistAccount1(userId2, accountRepository)

        when: "getTransaction called with user1 trying to access user2's account"
            transactionService.getTransaction(userId1.toString(), user2Account1, UUID.randomUUID())

        then: "AccountNotFoundException thrown"
            thrown(AccountNotFoundException)
    }

    def "BACKEND-003-AC-16: throws TransactionNotFoundException for a non-existent transaction"() {
        given: "a valid user and account"
            UUID userId1 = createUser(userRepository)
            UUID user1Account1 = createAndPersistAccount1(userId1, accountRepository)

        when: "getTransaction called with random transactionId"
            transactionService.getTransaction(userId1.toString(), user1Account1, UUID.randomUUID())

        then: "TransactionNotFoundException thrown"
            thrown(TransactionNotFoundException)
    }

    def "BACKEND-003-AC-16: throws TransactionNotFoundException for a non-owned transaction"() {
        given: "valid user with valid account - user1"
            UUID userId1 = createUser(userRepository)
            UUID user1Account1 = createAndPersistAccount1(userId1, accountRepository)

        and: "valid user with valid account and associated transaction - user2"
            UUID userId2 = createUser2(userRepository)
            UUID user2Account1 = createAndPersistAccount1(userId2, accountRepository)
            UUID user2account1Transaction1 = createAndPersistPositiveTransaction(user2Account1, transactionRepository)

        when: "getTransaction called with user1 trying to access transaction which exists on user2's account"
            transactionService.getTransaction(userId1.toString(), user1Account1, user2account1Transaction1)

        then: "TransactionNotFoundException thrown"
            thrown(TransactionNotFoundException)
    }

    def "BACKEND-003-AC-16: throws TransactionNotFoundException for a transaction on a different account"() {
        given: "valid user with two valid accounts - user1"
            UUID userId1 = createUser(userRepository)
            UUID user1Account1 = createAndPersistAccount1(userId1, accountRepository)
            UUID user1Account2 = createAndPersistAccount2(userId1, accountRepository)

        and: "account2 has a valid transaction"
            UUID user1account2Transaction1 = createAndPersistPositiveTransaction(user1Account2, transactionRepository)

        when: "getTransaction called with user1 trying to access transaction on a different account"
            transactionService.getTransaction(userId1.toString(), user1Account1, user1account2Transaction1)

        then: "TransactionNotFoundException thrown"
            thrown(TransactionNotFoundException)
    }

}
