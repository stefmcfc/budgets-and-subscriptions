package uk.co.stefirby.budgetsandsubscriptions.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim;
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);

    private static final String INVALID_JWT = "Invalid JWT";

    private final Clock clock;
    private final String secret;
    private final int expiryMinutes;
    private final SecretKey secretKey;

    public JwtService(Clock clock,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.cookie.expiry-minutes}") int expiryMinutes) {
        this.clock = clock;
        this.secret = secret;
        this.expiryMinutes = expiryMinutes;
        this.secretKey = generateSecretKey();
    }

    // Jwts Builder needs Java.Util.Date so Sonar warning is a false positive here
    @SuppressWarnings("java:S2143")
    @Nullable public String createJWT(String userId, String role) {

        Instant now = Instant.now(clock);
        Instant expiry = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(expiry);

        return Jwts.builder()
                .issuer("budgets-and-subscriptions")
                .subject(userId)
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public JwtClaim getClaim(String jwt) {
        Claims claims;
        try {
            claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(jwt)
                        .getPayload();
        } catch (ExpiredJwtException _) {
            LOG.trace("{} - expired JWT", INVALID_JWT);
            throw new JwtParserException(INVALID_JWT);
        } catch (JwtException ex) {
            LOG.error(INVALID_JWT, ex);
            throw new JwtParserException(INVALID_JWT);
        }

        return new JwtClaim(getSubject(claims), getRole(claims));
    }

    private static String getSubject(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isEmpty()) {
            LOG.warn("{} - missing subject", INVALID_JWT);
            throw new JwtParserException(INVALID_JWT);
        }

        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException _) {
            LOG.warn("{} - Invalid subject", INVALID_JWT);
            throw new JwtParserException(INVALID_JWT);
        }

        return subject;
    }

    private static String getRole(Claims claims) {
        String role = (String) claims.get("role");
        if (role != null && !role.isEmpty()) {
            return role;
        } else {
            LOG.warn("{} - missing role", INVALID_JWT);
            throw new JwtParserException(INVALID_JWT);
        }
    }

    private SecretKey generateSecretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
}
