package uk.co.stefirby.budgetsandsubscriptions.service

import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification
import spock.lang.Subject
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse
import uk.co.stefirby.budgetsandsubscriptions.exception.DuplicateEmailException
import uk.co.stefirby.budgetsandsubscriptions.model.Role
import uk.co.stefirby.budgetsandsubscriptions.model.User
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

class AuthServiceSpec extends Specification {
    @Subject AuthService authService
    UserRepository mockUserRepository = Mock()
    PasswordEncoder mockPasswordEncoder = Mock()
    User mockUser = Mock()

    def setup() {
        authService = new AuthService(mockUserRepository, mockPasswordEncoder)
    }

    def "given user already exists, exception is thrown"() {
        given: "user in userRepository"
            String email = "user.in.repo@test.com"
            String password = "dummyPassword"
            String displayName = "Test"
            User userInRepo = new User(email, password, displayName)
            mockUserRepository.findByEmail(email) >> Optional.<User>of(userInRepo)

        when: "RegisterResponse is called with existing user"
            RegisterRequest regRequest = new RegisterRequest(email, password, displayName)
            authService.register(regRequest)

        then: "DuplicateEmailException is thrown"
            thrown(DuplicateEmailException)
    }

    def "given new user, response is returned"() {
        given: "user not in userRepository"
            String email = "new.user@test.com"
            String password = "dummyPassword"
            String encodedPassword = "encodedPassword"
            String displayName = "Test"
            mockUserRepository.findByEmail(email) >> Optional.empty()
            mockPasswordEncoder.encode(password) >> encodedPassword
            mockUserRepository.saveAndFlush(_ as User) >> mockUser
            mockUser.role >> Role.USER
            mockUser.displayName >> displayName
            mockUser.email >> email
            UUID uuid = UUID.randomUUID()
            mockUser.id >> uuid

        when: "RegisterResponse is called with existing user"
            RegisterRequest regRequest = new RegisterRequest(email, password, displayName)
            RegisterResponse response = authService.register(regRequest)

        then: "response fields are set as expected"
            response.email == email
            response.id == uuid
            response.displayName == displayName
            response.role == Role.USER.toString()
    }
}
