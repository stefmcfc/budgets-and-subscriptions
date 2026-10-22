package uk.co.stefirby.budgetsandsubscriptions.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException
import uk.co.stefirby.budgetsandsubscriptions.security.util.JwtHelper

import javax.crypto.SecretKey
import java.time.Clock

import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.ROLE_USER
import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.TEST_KEY

class JwtServiceSpec extends Specification {

    @Subject
    JwtService jwtService

    Clock clock = Clock.systemDefaultZone()

    SecretKey secretKey

    JwtHelper jwtHelper

    def setup() {
        jwtService = new JwtService(clock, TEST_KEY, 15)
        secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_KEY))
        jwtHelper = new JwtHelper()
    }

    def "AUTH-001-AC-12: access token claims are limited to id and role"() {
        given: "a user and role"
        String user = UUID.randomUUID().toString()

        when: "a JWT is built with that role"
        String jwt = jwtService.createJWT(user, ROLE_USER)

        then: "the issued JWT's claims contain only the user's id and role"
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jwt).getPayload()
        claims.containsKey("sub")
        claims.get("sub") == user
        claims.containsKey("role")
        claims.get("role") == ROLE_USER
        !claims.containsKey("email")
        !claims.containsKey("displayName")
        !claims.containsKey("passwordHash")

    }

    def "AUTH-001-AC-24: parsed JWT containing id in sub returns successfully"() {
        given: "jwt with valid user"
        String validUser = UUID.randomUUID().toString()
        String jwt = jwtHelper.buildJwt(validUser, ROLE_USER)

        when: "getUserId is called with this jwt"
        JwtClaim result = jwtService.getClaim(jwt)

        then: "userID returned as expected"
        result.subject() == validUser
        result.role() == ROLE_USER
    }

    @Unroll
    def "AUTH-001-AC-24: parsed JWT with #scenario throws exception"() {
        given: "jwt with no subject"
        String jwt = jwtHelper.buildJwt(subject, role)

        when: "getUserId is called with this jwt"
        jwtService.getClaim(jwt)

        then: "JwtParserException thrown"
        thrown(JwtParserException)

        where:
        scenario          | subject                      | role
        "missing subject" | null                         | ROLE_USER
        "empty subject"   | ""                           | ROLE_USER
        "invalid subject" | "not a valid uuid"           | ROLE_USER
        "missing role"    | UUID.randomUUID().toString() | null
        "empty role"      | UUID.randomUUID().toString() | ""
    }
}
