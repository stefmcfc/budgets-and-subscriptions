package uk.co.stefirby.budgetsandsubscriptions.security.util

import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys

import javax.crypto.SecretKey
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

import static uk.co.stefirby.budgetsandsubscriptions.common.TestConstants.TEST_KEY

class JwtHelper {

    private static String DIFFERENT_TEST_KEY = "n7Qx2mV9Kp4R8cL1wY6sD3aF0uHjT5eBqN2iGxZrP8o="

    Clock clock

    JwtHelper() {
        clock = Clock.systemDefaultZone()
    }

    String buildJwt(String subject, String role) {
        return createJwt(subject, role, false, false)
    }

    String buildExpiredJwt(String subject, String role) {
        return createJwt(subject, role, true, false)
    }

    String buildJwtWithDifferentKey(String subject, String role) {
        return createJwt(subject, role, false, true)
    }

    private String createJwt(String subject, String role, boolean expired, boolean alternateKey) {
        Instant now = Instant.now(clock)
        Instant issued
        Instant expiry
        if (!expired) {
            issued = now
            expiry = now.plus(15, ChronoUnit.MINUTES)
        } else {
            issued = now.minus(30, ChronoUnit.MINUTES)
            expiry = now.minus(15,  ChronoUnit.MINUTES)
        }
        Date issuedAt = Date.from(issued)
        Date expiresAt = Date.from(expiry)

        SecretKey secretKey
        if (!alternateKey) {
            secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_KEY))
        } else {
            secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(DIFFERENT_TEST_KEY))
        }

        JwtBuilder builder = Jwts.builder()
                .issuer("budgets-and-subscriptions-test")
                .issuedAt(issuedAt)
                .expiration(expiresAt)

        if (subject != null) {
            builder.subject(subject)
        }
        if (role != null) {
            builder.claim("role", role)
        }

        return builder
                .signWith(secretKey)
                .compact()
    }

}
