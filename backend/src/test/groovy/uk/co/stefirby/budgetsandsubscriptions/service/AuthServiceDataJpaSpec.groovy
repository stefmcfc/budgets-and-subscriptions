package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.exception.LoginException
import uk.co.stefirby.budgetsandsubscriptions.model.User
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class AuthServiceDataJpaSpec extends Specification {

    private static final String LOWERCASE_EMAIL = "test@test.com"
    private static final String UPPERCASE_EMAIL = "TEST@TEST.COM"
    private static final String DUMMY_PASSWORD = "dummyPassword"
    private static final String DIFFERENT_PASSWORD = "differentPassword"
    private static final String DISPLAY_NAME = "Test"

    @Subject
    AuthService authService

    @Autowired
    UserRepository userRepository

    PasswordEncoder passwordEncoder

    def setup() {
        passwordEncoder = new BCryptPasswordEncoder()
        authService = new AuthService(userRepository, passwordEncoder)
    }

    //---------------------------------
    // Registration tests
    //=================================

    def "AUTH-001-AC-05: when a new user registers, the email is stored as lowercase and the persisted password is not plaintext"() {
        given: "a new user is registered"
        def newRegisterRequest = new RegisterRequest(UPPERCASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)

        when: "register is called"
        authService.register(newRegisterRequest)

        then: "AUTH-001-AC-18: the new user (lower case) is found in the repository"
        def user = userRepository.findByEmail(LOWERCASE_EMAIL)
        user.isPresent()

        and: "the persisted password is a BCrypt hash, not the plaintext value"
        String hashedPassword = user.get().getPasswordHash()
        hashedPassword != DUMMY_PASSWORD

        passwordEncoder.matches(DUMMY_PASSWORD, hashedPassword)
    }

    def "AUTH-001-AC-06: rejects registration with a duplicate email"() {
        given: "a persisted user with a given email"
        def lowercaseUser = new User(LOWERCASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
        userRepository.saveAndFlush(lowercaseUser)

        when: "register is called with the same email address (different case)"
        def newRegisterRequest = new RegisterRequest(UPPERCASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
        authService.register(newRegisterRequest)

        then: "exception thrown"
        thrown(DuplicateEmailException)

        and: "DB only contains original user"
        userRepository.findAll().size() == 1
    }

    //---------------------------------
    // Login tests
    //=================================

    def "AUTH-001-AC-10: when a returning customer logs in, lower case email address is checked"() {
        given: "user is registered"
        def newRegisterRequest = new RegisterRequest(LOWERCASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
        authService.register(newRegisterRequest)

        when: "AUTH-001-AC-18: login is called with upper case email"
        LoginRequest loginRequest = new LoginRequest(UPPERCASE_EMAIL, DUMMY_PASSWORD)
        RegisterResponse result = authService.login(loginRequest)

        then: "response brings back real values from DB"
        userRepository.findByEmail(LOWERCASE_EMAIL).isPresent()
        User dbUser = userRepository.findByEmail(LOWERCASE_EMAIL).get()
        result.role() == dbUser.role.toString()
        result.displayName() == dbUser.displayName
        result.email() == dbUser.email
        result.id() == dbUser.id
    }

    def "AUTH-001-AC-11: when a non-existent user tries to login, LoginException is thrown"() {
        when: "login is called for a customer not yet in DB"
        LoginRequest loginRequest = new LoginRequest(LOWERCASE_EMAIL, DUMMY_PASSWORD)
        authService.login(loginRequest)

        then: "LoginException is thrown"
        thrown(LoginException)
    }

    def "AUTH-001-AC-11: when a existing user tries to login with incorrect password, LoginException is thrown"() {
        given: "user is registered"
        def newRegisterRequest = new RegisterRequest(LOWERCASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
        authService.register(newRegisterRequest)

        when: "login is called using a different password"
        LoginRequest loginRequest = new LoginRequest(LOWERCASE_EMAIL, DIFFERENT_PASSWORD)
        authService.login(loginRequest)

        then: "LoginException is thrown"
        thrown(LoginException)
    }
}
