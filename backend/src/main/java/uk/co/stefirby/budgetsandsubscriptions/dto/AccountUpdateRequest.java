package uk.co.stefirby.budgetsandsubscriptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType;

public record AccountUpdateRequest(
    String bankName,
    String accountName,
    AccountType type
) {
    @JsonIgnore
    @AssertTrue(message = "At least one of bankName, accountName or type must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return bankName != null || accountName != null || type != null;
    }

    @JsonIgnore
    @AssertTrue(message = "bankName and accountName must not be blank when provided")
    public boolean isProvidedTextFieldsNotBlank() {
        return (bankName == null || !bankName.isBlank())
            && (accountName == null || !accountName.isBlank());
    }
}
