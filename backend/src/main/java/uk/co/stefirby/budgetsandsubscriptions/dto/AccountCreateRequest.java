package uk.co.stefirby.budgetsandsubscriptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import uk.co.stefirby.budgetsandsubscriptions.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountCreateRequest(
        @NotBlank
        String bankName,

        @NotBlank
        String accountName,

        @NotNull
        AccountType type,

        @NotNull
        BigDecimal openingBalance,

        @NotNull
        @PastOrPresent
        LocalDate openedOn
) {
}
