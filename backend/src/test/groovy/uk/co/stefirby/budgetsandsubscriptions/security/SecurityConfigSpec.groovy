package uk.co.stefirby.budgetsandsubscriptions.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import spock.lang.Specification

class SecurityConfigSpec extends Specification {

    SecurityConfig securityConfig = new SecurityConfig()

    def "the configured PasswordEncoder hashes with BCrypt, never plaintext"() {
        when: "the passwordEncoder bean is created"
        def encoder = securityConfig.passwordEncoder()

        then: "it is a BCrypt encoder"
        encoder instanceof BCryptPasswordEncoder

        and: "encoding a password never returns the plaintext value"
        def encoded = encoder.encode("s3cret")
        encoded != "s3cret"

        and: "the encoded value verifies against the original password"
        encoder.matches("s3cret", encoded)
    }
}
