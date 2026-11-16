package uk.co.stefirby.budgetsandsubscriptions.model.util

import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionUpdateRequest
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction
import uk.co.stefirby.budgetsandsubscriptions.repository.TransactionRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionConstants.*

class TransactionEntityHelper {

    // Transaction

    static UUID createAndPersistPositiveTransaction(UUID accountId, TransactionRepository transactionRepository) {
        Transaction transaction = positiveTransaction(accountId)
        Transaction createdTransaction = transactionRepository.saveAndFlush(transaction)
        return createdTransaction.id
    }

    static UUID createAndPersistNegativeTransaction(UUID accountId, TransactionRepository transactionRepository) {
        Transaction transaction = negativeTransaction(accountId)
        Transaction createdTransaction = transactionRepository.saveAndFlush(transaction)
        return createdTransaction.id
    }

    static UUID createAndPersistZeroTransaction(UUID accountId, TransactionRepository transactionRepository) {
        Transaction transaction = zeroTransaction(accountId)
        Transaction createdTransaction = transactionRepository.saveAndFlush(transaction)
        return createdTransaction.id
    }

    static Transaction positiveTransaction(UUID accountId) {
        return new Transaction(accountId, TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    static Transaction negativeTransaction(UUID accountId) {
        return new Transaction(accountId, TRANSACTION_AMOUNT_NEGATIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    static Transaction zeroTransaction(UUID accountId) {
        return new Transaction(accountId, TRANSACTION_AMOUNT_ZERO, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    // TransactionCreateRequest

    static TransactionCreateRequest positiveTransactionCreateRequest() {
        return new TransactionCreateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    static TransactionCreateRequest transactionCreateRequestBeforeOpenedOn() {
        return new TransactionCreateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_BEFORE_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    // TransactionUpdateRequest

    static TransactionUpdateRequest transactionUpdateRequest() {
        return new TransactionUpdateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    static TransactionUpdateRequest transactionUpdateRequestBeforeOpenedOn() {
        return new TransactionUpdateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_BEFORE_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

    static TransactionUpdateRequest transactionUpdateRequestBlankDescription() {
        return new TransactionUpdateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            "", TRANSACTION_RAW_DESCRIPTION)
    }

    static TransactionUpdateRequest transactionUpdateRequestBlankRawDescription() {
        return new TransactionUpdateRequest(TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, "")
    }

    // TransactionResponse

    static TransactionResponse transactionResponse() {
        return new TransactionResponse(UUID.randomUUID(), TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }
    static TransactionResponse transactionResponseWithNullRawDescription() {
        return new TransactionResponse(UUID.randomUUID(), TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, null)
    }

}
