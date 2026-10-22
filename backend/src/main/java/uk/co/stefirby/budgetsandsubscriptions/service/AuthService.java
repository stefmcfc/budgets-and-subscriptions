package uk.co.stefirby.budgetsandsubscriptions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.UserResponse;
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException;
import uk.co.stefirby.budgetsandsubscriptions.exception.LoginException;
import uk.co.stefirby.budgetsandsubscriptions.exception.UserException;
import uk.co.stefirby.budgetsandsubscriptions.model.User;
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_HASH = "$2a$10$lpv0VpOQoLXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXDummyPassword";
    protected static final String MESSAGE_DUPLICATE_EMAIL = "A user already exists with this email address";
    protected static final String MESSAGE_LOGIN_ERROR = "Unable to login";
    protected static final String MESSAGE_USER_ERROR = "User not found";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // at this point validation of fields has already happened behind the scenes.

        // first check to see if user (email address) already exists.
        String email = request.email().toLowerCase();
        Optional<User> userCheck = userRepository.findByEmail(email);
        if (userCheck.isPresent()) {
            LOG.warn(MESSAGE_DUPLICATE_EMAIL);
            throw new DuplicateEmailException(MESSAGE_DUPLICATE_EMAIL);
        }

        // create the user
        String displayName = request.displayName();
        String encodedPassword = passwordEncoder.encode(request.password());
        User newUser = new User(email, encodedPassword, displayName);
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException _) {
            LOG.warn(MESSAGE_DUPLICATE_EMAIL);
            throw new DuplicateEmailException(MESSAGE_DUPLICATE_EMAIL);
        }

        // fetch the details from the db
        return new UserResponse(savedUser.getId(), savedUser.getEmail(),
                savedUser.getDisplayName(), savedUser.getRole().toString());

    }

    @Transactional
    public UserResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        Optional<User> userCheck = userRepository.findByEmail(email);
        if (userCheck.isEmpty()) {
            // run a dummy passwordEncoder.matches() against a constant hash on the not-found path
            // so both branches take the same time
            passwordEncoder.matches(request.password(), DUMMY_HASH);

            LOG.warn("{} (user does not exist)", MESSAGE_LOGIN_ERROR);
            throw new LoginException(MESSAGE_LOGIN_ERROR);
        }

        User user = userCheck.get();
        String passwordHash = user.getPasswordHash();
        String password = request.password();
        if (!passwordEncoder.matches(password, passwordHash)) {
            LOG.warn("{} (incorrect password)", MESSAGE_LOGIN_ERROR);
            throw new LoginException(MESSAGE_LOGIN_ERROR);
        }

        return new UserResponse(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getRole().toString());
    }

    @Transactional
    public UserResponse getUserDetailsById(String userId) {
        Optional<User> userCheck = userRepository.findById(UUID.fromString(userId));

        if (userCheck.isEmpty()) {
            LOG.warn("{} (user not found in DB)", MESSAGE_USER_ERROR);
            throw new UserException(MESSAGE_USER_ERROR);
        }

        User user = userCheck.get();

        return new UserResponse(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getRole().toString());
    }
}
