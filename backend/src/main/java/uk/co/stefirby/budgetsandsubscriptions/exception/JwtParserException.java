package uk.co.stefirby.budgetsandsubscriptions.exception;

public class JwtParserException extends RuntimeException {

    public JwtParserException(String message) {
        super(message);
    }

    public JwtParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
