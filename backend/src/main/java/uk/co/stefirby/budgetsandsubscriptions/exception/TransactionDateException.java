package uk.co.stefirby.budgetsandsubscriptions.exception;

public class TransactionDateException extends RuntimeException {
    public TransactionDateException(String message) {
        super(message);
    }

    public TransactionDateException(String message, Throwable cause) {
        super(message, cause);
    }
}
