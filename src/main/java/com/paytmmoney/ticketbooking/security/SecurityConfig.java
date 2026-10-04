package com.paytmmoney.ticketbooking.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.OffsetDateTime;

/**
 * Spring Security configuration for stateless JWT-based authentication.
 *
 * <h3>Authorization Matrix</h3>
 * <pre>
 * POST  /auth/token          → permitAll    (token generation for evaluators/burst scripts)
 * GET   /actuator/**         → permitAll    (health, metrics for monitoring)
 * GET   /shows/**            → permitAll    (public seat availability — no login required)
 * POST  /shows               → ADMIN only   (create show)
 * POST  /shows/{id}/reserve  → authenticated (book seats — valid JWT required)
 * POST  /reservations/{id}/cancel → authenticated (cancel booking — valid JWT required)
 * *     /**                  → authenticated (deny all else by default)
 * </pre>
 *
 * <h3>Key Settings</h3>
 * <ul>
 *   <li>CSRF disabled — stateless REST API; no browser session cookies.</li>
 *   <li>Sessions: STATELESS — Spring never creates an HttpSession.</li>
 *   <li>JWT filter inserted before UsernamePasswordAuthenticationFilter.</li>
 *   <li>Custom 401/403 handlers return JSON instead of HTML error pages.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF: disabled for stateless REST API ────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Session: never create or use HttpSession ─────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization Rules (first match wins) ───────────────────────
            .authorizeHttpRequests(auth -> auth

                // Token generation: open to all (evaluation/burst-testing support)
                .requestMatchers(HttpMethod.POST, "/auth/token").permitAll()

                // Monitoring endpoints: open to all
                .requestMatchers("/actuator/**").permitAll()

                // Seat availability: public — no login required
                .requestMatchers(HttpMethod.GET, "/shows/**").permitAll()

                // Show creation: admin only
                .requestMatchers(HttpMethod.POST, "/shows").hasRole("ADMIN")

                // Seat reservation: any authenticated user
                .requestMatchers(HttpMethod.POST, "/shows/*/reserve").authenticated()

                // Cancellation: any authenticated user (ownership check is in service layer)
                .requestMatchers(HttpMethod.POST, "/reservations/*/cancel").authenticated()

                // Default: require authentication for anything not explicitly matched
                .anyRequest().authenticated()
            )

            // ── Exception Handling: JSON responses instead of HTML ────────────
            .exceptionHandling(ex -> ex

                // 401: missing or invalid JWT
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        String.format(
                            "{\"status\":401,\"message\":\"Unauthorized: %s\",\"timestamp\":\"%s\"}",
                            authException.getMessage(),
                            OffsetDateTime.now()
                        )
                    );
                })

                // 403: authenticated but insufficient role
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                        String.format(
                            "{\"status\":403,\"message\":\"Forbidden: %s\",\"timestamp\":\"%s\"}",
                            accessDeniedException.getMessage(),
                            OffsetDateTime.now()
                        )
                    );
                })
            )

            // ── JWT Filter: insert before form-login filter ───────────────────
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
