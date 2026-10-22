package uk.co.stefirby.budgetsandsubscriptions.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uk.co.stefirby.budgetsandsubscriptions.dto.LoginRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.RegisterRequest;
import uk.co.stefirby.budgetsandsubscriptions.dto.UserResponse;
import uk.co.stefirby.budgetsandsubscriptions.security.JwtService;
import uk.co.stefirby.budgetsandsubscriptions.service.AuthService;

import java.time.Duration;
import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final int expiryMinutes;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          @Value("${app.cookie.expiry-minutes}") int expiryMinutes) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.expiryMinutes = expiryMinutes;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest requestBody) {
        UserResponse response = authService.register(requestBody);
        ResponseCookie cookie = createCookie(response.id().toString(), response.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest loginBody) {
        UserResponse response = authService.login(loginBody);
        ResponseCookie cookie = createCookie(response.id().toString(), response.role());
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal String userId) {
        UserResponse response = authService.getUserDetailsById(userId);
        return ResponseEntity.ok(response);
    }

    private ResponseCookie createCookie(String userId, String role) {
        return ResponseCookie
                .from(COOKIE_ACCESS_TOKEN, jwtService.createJWT(userId, role))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(expiryMinutes))
                .build();
    }

}
