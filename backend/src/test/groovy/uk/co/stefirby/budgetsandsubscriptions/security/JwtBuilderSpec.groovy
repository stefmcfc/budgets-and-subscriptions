package uk.co.stefirby.budgetsandsubscriptions.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import spock.lang.Specification
import spock.lang.Subject

import javax.crypto.SecretKey
import java.time.Clock

class JwtBuilderSpec extends Specification {

    public static final String TEST_SECRET = "IhUNfZ7p3F47zGL82csFIa9ehgv7LNpj9fHVDVIIriI="

    @Subject
    JwtBuilder jwtBuilder

    Clock clock = Clock.systemDefaultZone()

    def setup() {
        jwtBuilder = new JwtBuilder(clock, TEST_SECRET, 15)
    }

    def "AUTH-001-AC-12: access token claims are limited to id and role"() {
        given: "a user and role"
        String user = UUID.randomUUID().toString()
        String role = "USER"
        SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));

        when: "a JWT is built with that role"
        String jwt = jwtBuilder.createJWT(user, role)

        then: "the issued JWT's claims contain only the user's id and role"
        def claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jwt).getPayload()
        claims.containsKey("sub")
        claims.get("sub") == user
        claims.containsKey("role")
        claims.get("role") == role
        !claims.containsKey("email")
        !claims.containsKey("displayName")
        !claims.containsKey("passwordHash")

    }

}
