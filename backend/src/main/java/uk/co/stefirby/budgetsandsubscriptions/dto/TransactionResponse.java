package uk.co.stefirby.budgetsandsubscriptions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        LocalDate date,
        String description,
        String rawDescription
) {
}
