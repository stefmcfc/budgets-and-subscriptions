package uk.co.stefirby.budgetsandsubscriptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionUpdateRequest(
        BigDecimal amount,
        LocalDate date,
        String description,
        String rawDescription
) {
    @JsonIgnore
    @AssertTrue(message = "At least one of amount, date, description or rawDescription must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return amount != null || date != null || description != null || rawDescription != null;
    }

    @JsonIgnore
    @AssertTrue(message = "description must not be blank when provided")
    public boolean isProvidedTextFieldsNotBlank() {
        return description == null || !description.isBlank();
    }
}
