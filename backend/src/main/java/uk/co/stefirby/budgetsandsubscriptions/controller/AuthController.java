package uk.co.stefirby.budgetsandsubscriptions.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterResponse;
import uk.co.stefirby.budgetsandsubscriptions.security.JwtBuilder;
import uk.co.stefirby.budgetsandsubscriptions.service.AuthService;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtBuilder jwtBuilder;
    private final int expiryMinutes;

    public AuthController(AuthService authService,
                          JwtBuilder jwtBuilder,
                          @Value("${app.cookie.expiry-minutes}") int expiryMinutes) {
        this.authService = authService;
        this.jwtBuilder = jwtBuilder;
        this.expiryMinutes = expiryMinutes;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest requestBody) {
        RegisterResponse response = authService.register(requestBody);
        ResponseCookie cookie = createCookie(response.id().toString(), response.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<RegisterResponse> login(@Valid @RequestBody LoginRequest loginBody) {
        RegisterResponse response = authService.login(loginBody);
        ResponseCookie cookie = createCookie(response.id().toString(), response.role());
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie createCookie(String userId, String role) {
        return ResponseCookie
                .from("access_token", jwtBuilder.createJWT(userId, role))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(expiryMinutes))
                .build();
    }

}
