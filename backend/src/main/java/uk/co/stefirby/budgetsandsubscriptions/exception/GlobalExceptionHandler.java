package uk.co.stefirby.budgetsandsubscriptions.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_ERRORS = "Validation Errors";
    private static final String INVALID_REQUEST = "Invalid request";

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
                .body(ApiError.of(VALIDATION_ERRORS, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        LOG.warn("HttpMessageNotReadableException", ex);
        ApiError apiError;
        if (ex.getCause() instanceof InvalidFormatException ife) {
            String value = ife.getValue().toString();
            String propertyName = ife.getPath().getLast().getPropertyName();
            String errorMessage;
            if (ife.getTargetType().isEnum()) {
                String validValues = Arrays.stream(ife.getTargetType()
                    .getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
                errorMessage = String.format("[%s] is not valid. Must be one of %s", value, validValues);
            } else {
                String targetType = ife.getTargetType().getSimpleName();
                errorMessage = String.format("[%s] is not a valid %s", value, targetType);
            }
            apiError = ApiError.of(VALIDATION_ERRORS, Map.of(propertyName, errorMessage));
        } else {
            apiError = ApiError.of(VALIDATION_ERRORS);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        LOG.warn("MethodArgumentTypeMismatchException", ex);
        ApiError apiError;
        if (ex.getPropertyName() != null && ex.getValue() != null) {
            apiError = ApiError.of(INVALID_REQUEST, Map.of(ex.getPropertyName(), ex.getValue()));
        } else {
            apiError = ApiError.of(INVALID_REQUEST);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(apiError);
    }

    @ExceptionHandler({NoResourceFoundException.class, MissingPathVariableException.class})
    public ResponseEntity<ApiError> handleMalformedPathException(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("Malformed request"));
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

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFoundException(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
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
