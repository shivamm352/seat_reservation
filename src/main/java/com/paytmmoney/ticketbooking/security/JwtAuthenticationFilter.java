package com.paytmmoney.ticketbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-request JWT authentication filter.
 *
 * <h3>Processing Flow</h3>
 * <ol>
 *   <li>Extract Bearer token from {@code Authorization} header.</li>
 *   <li>Validate signature and expiry via {@link JwtService#validateToken}.</li>
 *   <li>Build {@link UserPrincipal} from token claims — <em>no DB query</em>.</li>
 *   <li>Store authentication in {@link SecurityContextHolder} for this request.</li>
 *   <li>Continue filter chain regardless — authorization is checked downstream
 *       by Spring Security's filter chain based on the configured rules.</li>
 * </ol>
 *
 * <h3>Why We Never 401 Here</h3>
 * <p>On invalid/missing tokens we simply skip setting authentication and pass
 * through. The {@link org.springframework.security.web.access.ExceptionTranslationFilter}
 * downstream handles the 401 via the configured {@code AuthenticationEntryPoint}.
 * This keeps the filter's responsibility narrowly focused: parse and set identity.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtService.validateToken(token)) {
            String userId = jwtService.extractUserId(token);
            String role   = jwtService.extractRole(token);

            UserPrincipal principal = new UserPrincipal(userId, role);

            // Create authentication token with authorities derived from role
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,                       // no credentials in stateless JWT
                            principal.getAuthorities()
                    );

            // Attach request metadata (IP, session ID) for audit logging
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            // Set into current request's security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Always continue — Spring Security's downstream filters enforce authorization
        filterChain.doFilter(request, response);
    }

    /**
     * Skips filter entirely for auth and actuator endpoints — they don't need JWT parsing.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/auth/token") || path.startsWith("/actuator/");
    }

    /**
     * Extracts the raw JWT from the {@code Authorization: Bearer <token>} header.
     *
     * @return The token string, or {@code null} if header is absent/malformed.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
