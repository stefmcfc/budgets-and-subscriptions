package uk.co.stefirby.budgetsandsubscriptions.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtBuilder {

    private final Clock clock;
    private final String secret;
    private final int expiryMinutes;

    public JwtBuilder(Clock clock,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.cookie.expiry-minutes}") int expiryMinutes) {
        this.clock = clock;
        this.secret = secret;
        this.expiryMinutes = expiryMinutes;
    }

    // Jwts Builder needs Java.Util.Date so Sonar warning is a false positive here
    @SuppressWarnings("java:S2143")
    @Nullable public String createJWT(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("role", role);

        Instant now = Instant.now(clock);
        Instant expiry = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(expiry);

        SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));

        return Jwts.builder()
                .issuer("budgets-and-subscriptions")
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
}
