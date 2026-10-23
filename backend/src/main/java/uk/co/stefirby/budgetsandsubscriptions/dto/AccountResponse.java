package uk.co.stefirby.budgetsandsubscriptions.dto;

import uk.co.stefirby.budgetsandsubscriptions.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        BigDecimal openingBalance,
        LocalDate openedOn,
        BigDecimal balance
) {
}
