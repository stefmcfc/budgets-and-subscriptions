package uk.co.stefirby.budgetsandsubscriptions.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.co.stefirby.budgetsandsubscriptions.dto.JwtClaim;
import uk.co.stefirby.budgetsandsubscriptions.exception.JwtParserException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static uk.co.stefirby.budgetsandsubscriptions.common.Constants.COOKIE_ACCESS_TOKEN;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> jwt = Arrays.stream(cookies)
                .filter(cookie -> COOKIE_ACCESS_TOKEN.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();

        if (jwt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtClaim jwtClaim = jwtService.getClaim(jwt.get());
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + jwtClaim.role()));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(jwtClaim.subject(), null, authorities);
            authentication.setDetails(jwtClaim);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtParserException _) {
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
