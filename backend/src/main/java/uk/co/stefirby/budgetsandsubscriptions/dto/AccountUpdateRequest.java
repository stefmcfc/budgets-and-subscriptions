package uk.co.stefirby.budgetsandsubscriptions.dto;

import uk.co.stefirby.budgetsandsubscriptions.model.AccountType;

public record AccountUpdateRequest(
        String name,
        AccountType type
) {
}
