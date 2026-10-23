package uk.co.stefirby.budgetsandsubscriptions.dto;

import uk.co.stefirby.budgetsandsubscriptions.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountCreateRequest(
        String name,
        AccountType type,
        BigDecimal openingBalance,
        LocalDate openedOn
) {
}
