package uk.co.stefirby.budgetsandsubscriptions.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        LOG.error("MethodArgumentNotValidException", ex);
        Map<String, List<String>> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(Collectors.groupingBy(
                                FieldError::getField,
                                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                        ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("Validation Errors", errors));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmailException(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(LoginException.class)
    public ResponseEntity<ApiError> handleLoginException(LoginException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiError> handleUserException(UserException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception ex) {
        LOG.error("Unhandled Exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Unhandled Exception"));
    }

}
