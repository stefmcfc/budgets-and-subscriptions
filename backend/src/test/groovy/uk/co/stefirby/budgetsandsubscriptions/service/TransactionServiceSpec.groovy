package uk.co.stefirby.budgetsandsubscriptions.service

import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.AccountNotFoundException
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
}
