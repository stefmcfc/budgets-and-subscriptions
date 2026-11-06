package uk.co.stefirby.budgetsandsubscriptions.service

import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionNotFoundException
import uk.co.stefirby.budgetsandsubscriptions.exception.TransactionDateException
import uk.co.stefirby.budgetsandsubscriptions.model.Account
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction
import uk.co.stefirby.budgetsandsubscriptions.repository.AccountRepository
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository
import uk.co.stefirby.budgetsandsubscriptions.service.common.AccountAccessService

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
}
