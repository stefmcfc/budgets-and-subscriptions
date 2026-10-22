package uk.co.stefirby.budgetsandsubscriptions.security

import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

@ActiveProfiles("test")
@SpringBootTest
class SecurityConfigSpringBootSpec extends Specification {

    @Autowired
    SecurityFilterChain securityFilterChain

    def "AUTH-001-AC-23: SecurityConfig registers JwtAuthenticationFilter ahead of the AuthorizationFilter"() {
        given: "the configured SecurityFilterChain"
        List<Filter> filters = securityFilterChain.filters

        when: "the filter chain's filter order is inspected"
        def jwtIndex = filters.findIndexOf {it instanceof JwtAuthenticationFilter}
        def authorizationFilterIndex =
                filters.findIndexOf { it instanceof AuthorizationFilter }

        then: "JwtAuthenticationFilter runs before AuthorizationFilter"
        jwtIndex >= 0
        authorizationFilterIndex >= 0
        jwtIndex < authorizationFilterIndex
    }
}
