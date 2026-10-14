package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.model.User
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class AuthServiceDataJpaSpec extends Specification {

    private static final String VALID_EMAIL = "test@test.com"
    private static final String UPPERCASE_EMAIL = "TEST@TEST.COM"
    private static final String DUMMY_PASSWORD = "dummyPassword"
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

    def "AUTH-001-AC-05: when a new user registers, the persisted password is not plaintext"() {
        given: "a new user is registered"
        def newRegisterRequest = new RegisterRequest(VALID_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)

        when: "register is called"
        authService.register(newRegisterRequest)

        then: "the new user is found in the repository"
        def user = userRepository.findByEmail(VALID_EMAIL)
        user.isPresent()

        and: "the persisted password is a BCrypt hash, not the plaintext value"
        String hashedPassword = user.get().getPasswordHash()
        hashedPassword != DUMMY_PASSWORD
        passwordEncoder.matches(DUMMY_PASSWORD, hashedPassword)
    }

    @Unroll
    def "AUTH-001-AC-06: rejects registration with a duplicate email #scenario"() {
        given: "a persisted user with a given email"
        def lowercaseUser = new User(VALID_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
        userRepository.saveAndFlush(lowercaseUser)

        when: "register is called with the same email address"
        def newRegisterRequest = new RegisterRequest(email, DUMMY_PASSWORD, DISPLAY_NAME)
        authService.register(newRegisterRequest)

        then: "DuplicateEmailException is thrown"
        thrown(DuplicateEmailException)

        where:
        scenario | email
        //"same case"  | VALID_EMAIL
        "upper case" | UPPERCASE_EMAIL
    }
}
