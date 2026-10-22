package uk.co.stefirby.budgetsandsubscriptions.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.security.util.JwtHelper

import java.time.Clock

import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.ROLE_USER
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.TEST_KEY

class JwtAuthenticationFilterSpec extends Specification {

    @Subject
    JwtAuthenticationFilter jwtAuthenticationFilter

    JwtService jwtService

    @Shared
    String randomUuid = UUID.randomUUID().toString()
    @Shared
    JwtHelper jwtHelper = new JwtHelper()

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    FilterChain chain = Mock(FilterChain)

    def setup() {
        Clock clock = Clock.systemDefaultZone()
        jwtService = new JwtService(clock, TEST_KEY, 15)
    }

    def "AUTH-001-AC-20: populates the SecurityContext for a valid access-token cookie"() {
        given: "a request carrying a valid, unexpired, correctly-signed access-token cookie"
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService)

        String jwt = jwtService.createJWT(randomUuid, "USER")
        request.setCookies(new Cookie(COOKIE_ACCESS_TOKEN, jwt))

        SecurityContextHolder.clearContext()

        when: "the request passes through JwtAuthenticationFilter"
        jwtAuthenticationFilter.doFilter(request, response, chain)

        then: "SecurityContextHolder holds an authenticated Authentication for the token's user id and role"
        def authentication = SecurityContextHolder.context.authentication
        authentication.authenticated
        authentication.principal == randomUuid
        authentication.authorities*.authority == ["ROLE_USER"]

        cleanup:
        SecurityContextHolder.clearContext()
    }

    def "AUTH-001-AC-21: leaves the request unauthenticated when no cookie is present"() {
        given: "a request with no access-token cookie"
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService)

        SecurityContextHolder.clearContext()

        when: "the request passes through JwtAuthenticationFilter"
        jwtAuthenticationFilter.doFilter(request, response, chain)

        then: "SecurityContextHolder has no Authentication set"
        SecurityContextHolder.context.authentication == null

        and: "the filter chain continues to the next filter"
        1 * chain.doFilter(request, response)

        cleanup:
        SecurityContextHolder.clearContext()
    }

    @Unroll
    def "AUTH-001-AC-22: swallows a cookie that is #scenario without a 500"() {
        given: "a request whose access-token cookie causes JwtService to throw JwtParserException"
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService)
        request.setCookies(new Cookie(COOKIE_ACCESS_TOKEN, token))

        SecurityContextHolder.clearContext()

        when: "the request passes through JwtAuthenticationFilter"
        jwtAuthenticationFilter.doFilter(request, response, chain)

        then: "no exception propagates out of the filter"
        noExceptionThrown()

        then: "SecurityContextHolder has no Authentication set"
        SecurityContextHolder.context.authentication == null

        and: "the filter chain continues to the next filter"
        1 * chain.doFilter(request, response)

        cleanup:
        SecurityContextHolder.clearContext()

        where:
        scenario                   | token
        "is malformed"             | "not-a-jwt"
        "is expired"               | jwtHelper.buildExpiredJwt(randomUuid, ROLE_USER)
        "has an invalid-signature" | jwtHelper.buildJwtWithDifferentKey(randomUuid, ROLE_USER)
    }
}
