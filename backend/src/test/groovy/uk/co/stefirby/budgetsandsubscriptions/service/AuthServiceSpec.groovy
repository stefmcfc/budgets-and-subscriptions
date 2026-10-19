package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.exception.LoginException
import uk.co.stefirby.budgetsandsubscriptions.model.Role
import uk.co.stefirby.budgetsandsubscriptions.model.User
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

class AuthServiceSpec extends Specification {

    private static final String EMAIL = "test@test.com"
    private static final String MIXED_CASE_EMAIL = "TEST@Test.Com"
    private static final String DUMMY_PASSWORD = "dummyPassword"
    private static final String DISPLAY_NAME = "Test"
    private static final String ENCODED_PASSWORD = "encodedPassword"

    @Subject AuthService authService
    UserRepository mockUserRepository = Mock()
    PasswordEncoder mockPasswordEncoder = Mock()
    User mockUser = Mock()

    def setup() {
        authService = new AuthService(mockUserRepository, mockPasswordEncoder)
    }

    private void mockUserResponse(User mockUser, UUID randomUuid) {
        mockUser.getEmail() >> EMAIL
        mockUser.getDisplayName() >> DISPLAY_NAME
        mockUser.getPasswordHash() >> ENCODED_PASSWORD
        mockUser.getRole() >> Role.USER
        mockUser.getId() >> randomUuid
    }

    //-----------------------
    //  Registration tests
    //=======================

    def "AUTH-001-AC-06: given user already exists, exception is thrown (caught at findByEmail)"() {
        given: "user in first userRepository check"
            User userInRepo = new User(EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
            mockUserRepository.findByEmail(EMAIL) >> Optional.<User>of(userInRepo)

        when: "register is called with existing user"
            RegisterRequest regRequest = new RegisterRequest(EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
            authService.register(regRequest)

        then: "DuplicateEmailException is thrown"
            def ex = thrown(DuplicateEmailException)
            ex.message == AuthService.MESSAGE_DUPLICATE_EMAIL
    }

    def "AUTH-001-AC-06: given user already exists, exception is thrown (caught at saveAndFlush)"() {
        given: "user not in first userRepository check"
            mockUserRepository.findByEmail(EMAIL) >> Optional.empty()
            mockPasswordEncoder.encode(DUMMY_PASSWORD) >> ENCODED_PASSWORD

        and: "saveAndFlush catches DataIntegrityViolationException"
            mockUserRepository.saveAndFlush(_ as User) >> { throw new DataIntegrityViolationException("dummy message")}

        when: "register is called with existing user"
            RegisterRequest regRequest = new RegisterRequest(EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
            authService.register(regRequest)

        then: "DuplicateEmailException is thrown"
            def ex = thrown(DuplicateEmailException)
            ex.message == AuthService.MESSAGE_DUPLICATE_EMAIL
    }

    def "AUTH-001-AC-05: given new user, response is returned"() {
        given: "user not in first userRepository check"
            mockPasswordEncoder.encode(DUMMY_PASSWORD) >> ENCODED_PASSWORD

        and: "user saves successfully"
            UUID randomUuid = UUID.randomUUID()
            mockUserResponse(mockUser, randomUuid)

        when: "register is called with new user"
            RegisterRequest regRequest = new RegisterRequest(MIXED_CASE_EMAIL, DUMMY_PASSWORD, DISPLAY_NAME)
            RegisterResponse response = authService.register(regRequest)

        then: "response fields are set as expected"
            response.email == EMAIL
            response.id == randomUuid
            response.displayName == DISPLAY_NAME
            response.role == Role.USER.toString()

        and: "AUTH-001-AC-18: DB interactions only ever use lower case email"
            1 * mockUserRepository.findByEmail(MIXED_CASE_EMAIL.toLowerCase()) >> Optional.empty()
            1 * mockUserRepository.saveAndFlush({ User u -> u.email == MIXED_CASE_EMAIL.toLowerCase() }) >> mockUser
    }

    //-----------------------
    //  Login tests
    //=======================

    def "AUTH-001-AC-11: given user doesn't exist at login then exception is thrown"() {
        given: "user not in userRepository check"
            mockUserRepository.findByEmail(EMAIL) >> Optional.empty()

        when: "login is called"
            LoginRequest loginRequest = new LoginRequest(EMAIL, DUMMY_PASSWORD)
            authService.login(loginRequest)

        then: "LoginException is thrown"
            def ex = thrown(LoginException)
            ex.message == AuthService.MESSAGE_LOGIN_ERROR

        and: "passwordEncoder matches is called with a dummy value to keep wall clock consistent"
            1 * mockPasswordEncoder.matches(DUMMY_PASSWORD, _ as String)
    }

    def "AUTH-001-AC-11: given user exists but password is incorrect then exception is thrown"() {
        given: "user is in userRepository check"
            mockUserRepository.findByEmail(EMAIL) >> Optional.<User>of(mockUser)

        and: "password does not match"
            mockUser.getPasswordHash() >> ENCODED_PASSWORD
            mockPasswordEncoder.matches(DUMMY_PASSWORD, ENCODED_PASSWORD) >> false

        when: "login is called"
            LoginRequest loginRequest = new LoginRequest(EMAIL, DUMMY_PASSWORD)
            authService.login(loginRequest)

        then: "LoginException is thrown"
            def ex = thrown(LoginException)
            ex.message == AuthService.MESSAGE_LOGIN_ERROR
    }

    def "AUTH-001-AC-18: given mixed case email on login, case is made lower before DB call is executed"() {
        given: "request with mixed case email"
            LoginRequest loginRequest = new LoginRequest(MIXED_CASE_EMAIL, DUMMY_PASSWORD)

        when: "register is called"
            authService.login(loginRequest)

        then: "call is made with lower case email, and login fails (no matching user)"
            1 * mockUserRepository.findByEmail(MIXED_CASE_EMAIL.toLowerCase()) >> Optional.empty()
            thrown(LoginException)
    }

    def "AUTH-001-AC-10: given user exists and password is correct then response is returned"() {
        given: "password matches"
            UUID randomUuid = UUID.randomUUID()
            mockUserResponse(mockUser, randomUuid)
            mockPasswordEncoder.matches(DUMMY_PASSWORD, ENCODED_PASSWORD) >> true

        when: "login is called"
            LoginRequest loginRequest = new LoginRequest(MIXED_CASE_EMAIL, DUMMY_PASSWORD)
            RegisterResponse response = authService.login(loginRequest)

        then: "response fields are set as expected"
            response.email == EMAIL
            response.id == randomUuid
            response.displayName == DISPLAY_NAME
            response.role == Role.USER.toString()

        and: "AUTH-001-AC-18: DB interactions only ever use lower case email"
            1 * mockUserRepository.findByEmail(MIXED_CASE_EMAIL.toLowerCase()) >> Optional.<User>of(mockUser)
    }

}
