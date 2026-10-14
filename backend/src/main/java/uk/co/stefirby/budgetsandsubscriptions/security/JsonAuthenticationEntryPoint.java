package uk.co.stefirby.budgetsandsubscriptions.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;
import uk.co.stefirby.budgetsandsubscriptions.exception.ApiError;

import java.io.IOException;

/**
 * Responds to unauthenticated requests against a protected endpoint with a plain {@code 401} JSON
 * body — never a redirect to a server-rendered login page, since there isn't one for this JSON SPA.
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of("Authentication required"));
    }
}
