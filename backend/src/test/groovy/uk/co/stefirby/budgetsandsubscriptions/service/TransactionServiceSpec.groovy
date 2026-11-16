package uk.co.stefirby.budgetsandsubscriptions.service

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionUpdateRequest
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException
import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService

import java.time.LocalDate

import static uk.co.stefirby.budgetsandsubscriptions.model.util.AccountEntityHelper.*
import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionEntityHelper.*

class TransactionServiceSpec extends Specification {

    @Subject
    TransactionService transactionService

    AccountAccessService mockAccountAccessService = Mock()
    AccountRepository mockAccountRepository = Mock()
    TransactionRepository mockTransactionRepository = Mock()

    def setup() {
        transactionService = new TransactionService(
            mockAccountAccessService, mockAccountRepository, mockTransactionRepository)
    }

    //----------------------------
    // addTransaction tests
    //============================

    def "BACKEND-003-AC-06: given user and valid TransactionCreateRequest then addTransaction creates a transaction"() {
        given: "valid user with UUID format"
            UUID userId = UUID.randomUUID()
            UUID userAccount = UUID.randomUUID()
            Account account = account1(userId)

        and: "valid transactionCreateRequest"
            TransactionCreateRequest createRequest = positiveTransactionCreateRequest()

        and: "user and account found in DB"
            mockAccountAccessService.getAccountForUser(userId, userAccount) >> account

        when: "addTransaction is called"
            TransactionResponse response = transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "response is populated as expected"
            response != null

        and: "the transaction is added and account updated"
            1 * mockTransactionRepository.saveAndFlush(_ as Transaction) >> positiveTransaction(userAccount)
            1 * mockAccountRepository.saveAndFlush(_ as Account) >> account
    }

    def "BACKEND-003-AC-07: errors uniformly for a non-existent or non-owned account on create"() {
        given: "valid user with UUID format"
            UUID userId = UUID.randomUUID()
            UUID userAccount = UUID.randomUUID()

        and: "valid transactionCreateRequest"
            TransactionCreateRequest createRequest = positiveTransactionCreateRequest()

        and: "account not found"
            mockAccountAccessService.getAccountForUser(userId, userAccount) >> {
                throw new AccountNotFoundException("Account Not Found") }

        when: "addTransaction is called"
            transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "exception is propagated"
            thrown(AccountNotFoundException)

        and: "no transaction is created"
            0 * mockTransactionRepository.saveAndFlush(_ as Transaction)

        and: "account is not updated"
            0 * mockAccountRepository.saveAndFlush(_ as Account)
    }

    def "BACKEND-003-AC-10: rejects creation with a missing or too-early date"() {
        given: "valid user with UUID format"
            UUID userId = UUID.randomUUID()
            UUID userAccount = UUID.randomUUID()
            Account account = account1(userId)

        and: "transactionCreateRequest that falls before the Account openedOn"
            TransactionCreateRequest createRequest = transactionCreateRequestBeforeOpenedOn()

        and: "user and account found in DB"
            mockAccountAccessService.getAccountForUser(userId, userAccount) >> account

        when: "addTransaction is called"
            transactionService.addTransaction(userId.toString(), userAccount, createRequest)

        then: "TransactionDateException is thrown"
            thrown(TransactionDateException)

        and: "no transaction is created"
            0 * mockTransactionRepository.saveAndFlush(_ as Transaction)

        and: "account is not updated"
            0 * mockAccountRepository.saveAndFlush(_ as Account)
    }

    //----------------------------
    // getAllTransactions tests
    //============================

    def "BACKEND-003-AC-12: lists every transaction for an owned account"() {
        given: "an account owned by the authenticated user with several transactions recorded against it"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            Transaction transaction1 = positiveTransaction(accountId)
            Transaction transaction2 = negativeTransaction(accountId)

        when: "getAllTransactions is called"
            List<TransactionResponse> transactionsList =
                transactionService.getAllTransactions(userId.toString(), accountId)

        then: "AccountAccessService is called and verifies the user / account"
            1 * mockAccountAccessService.verifyAccountOwnership(_ as UUID, accountId)

        and: "TransactionRepository is called and returns transactions"
            1 * mockTransactionRepository.findAllByAccountId(accountId) >> List.of(transaction1, transaction2)

        and: "getAllTransactions returns the transaction responses"
            transactionsList.size() == 2
            transactionsList.any {it.id() == transaction1.id}
            transactionsList.any {it.id() == transaction2.id}
    }

    def "BACKEND-003-AC-13: returns an empty list for an owned account with no transactions"() {
        given: "an account owned by the authenticated user with no transactions"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()

        when: "getAllTransactions is called"
            List<TransactionResponse> transactionsList =
                transactionService.getAllTransactions(userId.toString(), accountId)

        then: "AccountAccessService is called and verifies the user / account"
            1 * mockAccountAccessService.verifyAccountOwnership(_ as UUID, accountId)

        and: "TransactionRepository is called and returns transactions"
            1 * mockTransactionRepository.findAllByAccountId(accountId) >> List.of()

        then: "getAllTransactions returns an empty list"
            transactionsList.size() == 0
    }

    def "BACKEND-003-AC-14: errors uniformly for a non-existent or non-owned account on list"() {
        given: "valid user with UUID format"
            UUID userId = UUID.randomUUID()
            UUID userAccount = UUID.randomUUID()

        and: "account not found"
            mockAccountAccessService.verifyAccountOwnership(userId, userAccount) >> {
                throw new AccountNotFoundException("Account Not Found") }

        when: "getAllTransactions is called"
            transactionService.getAllTransactions(userId.toString(), userAccount)

        then: "exception is propagated"
            thrown(AccountNotFoundException)

        and: "transactionRepository is not called"
            0 * mockTransactionRepository.findAllByAccountId(_ as UUID)
    }

    //----------------------------
    // getTransaction tests
    //============================

    def "BACKEND-003-AC-15: returns a single owned transaction"() {
        given: "a transaction owned (via its account) by the authenticated user"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()

            Transaction mockTransaction = positiveTransaction(accountId)

        when: "getTransaction is called"
            TransactionResponse response =
                transactionService.getTransaction(userId.toString(), accountId, transactionId)

        then: "AccountAccessService is called and verifies the user / account"
            1 * mockAccountAccessService.verifyAccountOwnership(userId, accountId)

        and: "transactionRepository is called and gets a transaction"
            1 * mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >>
                Optional.of(mockTransaction)

        and: "response carries expected mapped fields"
            response.id() == mockTransaction.id
            response.amount() == mockTransaction.amount
            response.date() == mockTransaction.date
            response.description() == mockTransaction.description
    }

    def "BACKEND-003-AC-16: AccountNotFoundException thrown for not-found/not-owned account"() {
        given: "userId, accountId and transactionId"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()

        and: "account not found"
            mockAccountAccessService.verifyAccountOwnership(userId, accountId) >> {
                throw new AccountNotFoundException("Account Not Found") }

        when: "getTransaction is called"
            transactionService.getTransaction(userId.toString(), accountId, transactionId)

        then: "exception is propagated"
            thrown(AccountNotFoundException)

        and: "transactionRepository is not called"
            0 * mockTransactionRepository.findByIdAndAccountId(_ as UUID, _ as UUID)
    }

    def "BACKEND-003-AC-16: TransactionNotFoundException thrown for cross-account/not-found transaction"() {
        given: "transactionId that does not exist or belongs to a different account"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()

        and: "findByIdAndAccountId returns empty"
            mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >> Optional.empty()

        when: "getTransaction is called"
            transactionService.getTransaction(userId.toString(), accountId, transactionId)

        then: "TransactionNotFoundException is thrown"
            thrown(TransactionNotFoundException)
    }

    //----------------------------
    // updateTransaction tests
    //============================

    @Unroll
    def "BACKEND-003-AC-18: updates an owned transaction when the request contains #scenario"() {
        given: "a transaction owned (via its account) by the authenticated user"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()
            Account account = account1(userId)
            Transaction transaction = positiveTransaction(accountId)

        and: "an update request with at least one of amount, date, description, or rawDescription present"
            TransactionUpdateRequest updateRequest =
                new TransactionUpdateRequest(newAmount, newDate, newDescription, newRawDescription)

        and: "AccountAccessService returns an account"
            mockAccountAccessService.getAccountForUser(userId, accountId) >> account

        and: "Transaction is found"
            mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >> Optional.of(transaction)

        when: "updateTransaction is called"
            TransactionResponse response =
                transactionService.updateTransaction(userId.toString(), accountId, transactionId, updateRequest)

        then: "transaction is updated"
            1 * mockTransactionRepository.saveAndFlush(_ as Transaction) >> { Transaction t -> t }

        and: "when amount is provided, new value is updated in Account and present in the response"
            if (newAmount != null) {
                response.amount() == newAmount
                1 * mockAccountRepository.saveAndFlush(_ as Account)
            } else {
                response.amount() == transaction.amount
                0 * mockAccountRepository.saveAndFlush(_ as Account)
            }

        and: "response is returned with updated fields for those in the request and unchanged for the others"
            if (newDate != null) {
                response.date() == newDate
            } else {
                response.date() == transaction.date
            }
            if (newDescription != null) {
                response.description() == newDescription
            } else {
                response.description() == transaction.description
            }
            if (newRawDescription != null) {
                response.rawDescription() == newRawDescription
            } else {
                response.rawDescription() == transaction.rawDescription
            }

        where:
            scenario               | newAmount      | newDate                   | newDescription    | newRawDescription
            "amount only"          | BigDecimal.TEN | null                      | null              | null
            "date only"            | null           | LocalDate.of(2026, 11, 1) | null              | null
            "description only"     | null           | null                      | "new description" | null
            "raw description only" | null           | null                      | null              | "new raw description"
            "all 4 fields"         | BigDecimal.TEN | LocalDate.of(2026, 11, 1) | "new description" | "new raw description"
    }

    def "BACKEND-003-AC-19: AccountNotFoundException thrown for not-found/not-owned account"() {
        given: "userId, accountId and transactionId"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()

        and: "a valid update request"
            TransactionUpdateRequest updateRequest = transactionUpdateRequest()

        and: "account not found"
            mockAccountAccessService.getAccountForUser(userId, accountId) >> {
                throw new AccountNotFoundException("Account Not Found") }

        when: "update is called"
            transactionService.updateTransaction(userId.toString(), accountId, transactionId, updateRequest)

        then: "exception is propagated"
            thrown(AccountNotFoundException)

        and: "transactionRepository is not called"
            0 * mockTransactionRepository.findByIdAndAccountId(_ as UUID, _ as UUID)
    }

    def "BACKEND-003-AC-19: TransactionNotFoundException thrown for cross-account/not-found transaction"() {
        given: "transactionId that does not exist or belongs to a different account"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()

        and: "a valid update request"
            TransactionUpdateRequest updateRequest = transactionUpdateRequest()

        and: "findByIdAndAccountId returns empty"
            mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >> Optional.empty()

        when: "update is called"
            transactionService.updateTransaction(userId.toString(), accountId, transactionId, updateRequest)

        then: "TransactionNotFoundException is thrown"
            thrown(TransactionNotFoundException)
    }

    def "BACKEND-003-AC-22: rejects an update with a present-but-too-early date"() {
        given: "a transaction owned (via its account) by the authenticated user"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()
            Account account = account1(userId)
            Transaction transaction = positiveTransaction(accountId)

        and: "an update request where date is present but earlier than the account's openedOn"
            TransactionUpdateRequest updateRequest = transactionUpdateRequestBeforeOpenedOn()

        and: "AccountAccessService returns an account"
            mockAccountAccessService.getAccountForUser(userId, accountId) >> account

        and: "Transaction is found"
            mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >> Optional.of(transaction)

        when: "updateTransaction is called"
            transactionService.updateTransaction(userId.toString(), accountId, transactionId, updateRequest)

        then: "TransactionDateException is thrown"
            thrown(TransactionDateException)
    }

    def "BACKEND-003-AC-23a: clears rawDescription to null when present but blank on update"() {
        given: "a transaction owned (via its account) by the authenticated user"
            UUID userId = UUID.randomUUID()
            UUID accountId = UUID.randomUUID()
            UUID transactionId = UUID.randomUUID()
            Account account = account1(userId)
            Transaction transaction = positiveTransaction(accountId)

        and: "an update request where rawDescription is present but blank"
            TransactionUpdateRequest updateRequest = transactionUpdateRequestBlankRawDescription()

        and: "AccountAccessService returns an account"
            mockAccountAccessService.getAccountForUser(userId, accountId) >> account

        and: "Transaction is found"
            mockTransactionRepository.findByIdAndAccountId(transactionId, accountId) >> Optional.of(transaction)

        when: "updateTransaction is called"
            TransactionResponse response =
                transactionService.updateTransaction(userId.toString(), accountId, transactionId, updateRequest)

        then: "transaction is updated"
            1 * mockTransactionRepository.saveAndFlush(_ as Transaction) >> { Transaction t -> t }

        and: "the transaction's rawDescription is null"
            response.rawDescription() == null
    }

}
