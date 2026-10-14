package uk.co.stefirby.budgetsandsubscriptions.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse;
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException;
import uk.co.stefirby.budgetsandsubscriptions.model.User;
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // at this point validation of fields has already happened behind the scenes.

        // first check to see if user (email address) already exists.
        String email = request.email();
        String displayName = request.displayName();
        Optional<User> userCheck = userRepository.findByEmail(request.email());
        if (userCheck.isPresent()) {
            throw new DuplicateEmailException("Email already exists (same case)");
        }

        // create the user
        String encodedPassword = passwordEncoder.encode(request.password());
        User newUser = new User(email, encodedPassword, displayName);
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException _) {
            throw new DuplicateEmailException("Email already exists (different case)");
        }

        // fetch the details from the db
        return new RegisterResponse(savedUser.getId(), savedUser.getEmail(),
                savedUser.getDisplayName(), savedUser.getRole().toString());

    }

}
