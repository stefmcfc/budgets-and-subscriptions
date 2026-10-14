package uk.co.stefirby.budgetsandsubscriptions.dto;

import java.util.UUID;

public record RegisterResponse (
        UUID id,
        String email,
        String displayName,
        String role
) { }