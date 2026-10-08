package uk.co.stefirby.budgetsandsubscriptions.dto;

import uk.co.stefirby.budgetsandsubscriptions.model.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Role role) {
}
