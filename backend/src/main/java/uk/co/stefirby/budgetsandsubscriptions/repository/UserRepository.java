package uk.co.stefirby.budgetsandsubscriptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.stefirby.budgetsandsubscriptions.model.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
