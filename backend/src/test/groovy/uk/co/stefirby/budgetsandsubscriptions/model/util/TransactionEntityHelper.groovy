package uk.co.stefirby.budgetsandsubscriptions.model.util

import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionCreateRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.TransactionResponse
import uk.co.stefirby.budgetsandsubscriptions.model.Transaction

import static uk.co.stefirby.budgetsandsubscriptions.model.util.TransactionConstants.*

class TransactionEntityHelper {

    // Transaction

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

    // TransactionResponse

    static TransactionResponse transactionResponse() {
        return new TransactionResponse(UUID.randomUUID(), TRANSACTION_AMOUNT_POSITIVE, TRANSACTION_DATE_AFTER_OPENED_ON,
            TRANSACTION_DESCRIPTION, TRANSACTION_RAW_DESCRIPTION)
    }

}
