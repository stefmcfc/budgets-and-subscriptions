package uk.co.stefirby.budgetsandsubscriptions.exception;

/**
 * The single JSON error shape returned for every handled error response across the API —
 * {@code { "message": "...", "details": null | {...} }}.
 */
public record ApiError(String message, Object details) {

    public static ApiError of(String message) {
        return new ApiError(message, null);
    }

    public static ApiError of(String message, Object details) {
        return new ApiError(message, details);
    }
}
