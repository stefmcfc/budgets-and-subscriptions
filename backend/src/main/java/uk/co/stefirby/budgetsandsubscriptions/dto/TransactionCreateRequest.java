package uk.co.stefirby.budgetsandsubscriptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateRequest(
        @NotNull
        BigDecimal amount,

        @NotNull
        LocalDate date,

        @NotBlank
        String description,

        String rawDescription
) {
}
