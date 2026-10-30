package uk.co.stefirby.budgetsandsubscriptions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionUpdateRequest(
        BigDecimal amount,
        LocalDate date,
        String description
) {
}
