package uk.co.stefirby.budgetsandsubscriptions.dto;

public record JwtClaim (
        String subject,
        String role
)
{ }
