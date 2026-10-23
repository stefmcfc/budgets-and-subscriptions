package uk.co.stefirby.budgetsandsubscriptions.model

import jakarta.validation.ConstraintViolationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.repository.UserRepository

import static uk.co.stefirby.budgetsandsubscriptions.model.ModelConstants.*

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
class UserSpec extends Specification {

    @Autowired
    UserRepository userRepository

    def "AUTH-001-AC-01: rejects a second user whose email differs only by case"() {
        given: "a persisted user with a given email"
            def lowercaseUser = new User(USER_EMAIL_VALID, USER_PASSWORD, USER_DISPLAY_NAME)
            userRepository.saveAndFlush(lowercaseUser)

        when: "a second user is persisted with the same email in different case"
            def uppercaseUser = new User(USER_EMAIL_UPPERCASE, USER_PASSWORD, USER_DISPLAY_NAME)
            userRepository.saveAndFlush(uppercaseUser)

        then: "persistence fails on the unique constraint"
            thrown(DataIntegrityViolationException)
    }

    def "AUTH-001-AC-02: defaults role to USER when not set"() {
        given: "a new User built without an explicit role"
            def lowercaseUser = new User(USER_EMAIL_VALID, USER_PASSWORD, USER_DISPLAY_NAME)

        when: "the user is persisted"
            userRepository.saveAndFlush(lowercaseUser)

        then: "the persisted user's role is USER"
            def repoResult = userRepository.findByEmail(USER_EMAIL_VALID)
            !repoResult.empty
            repoResult.get().role == Role.USER
    }

    @Unroll
    def "AUTH-001-AC-03: rejects a #scenario display name"() {
        given: "a new User with a null or blank displayName"
            def user = new User(USER_EMAIL_VALID, USER_PASSWORD, displayName)

        when: "the user is persisted"
            userRepository.saveAndFlush(user)

        then: "persistence fails validation"
            thrown(ConstraintViolationException)

        where:
            scenario | displayName
            "null"   | null
            "empty"  | ""
            "blank"  | "     "
    }

    def "AUTH-001-AC-04: application context loads with a schema-valid users table"() {
        expect: "the Spring context has started successfully with a valid UserRepository"
            userRepository != null
    }
}
