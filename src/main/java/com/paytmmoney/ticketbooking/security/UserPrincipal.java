package com.paytmmoney.ticketbooking.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Immutable security principal derived exclusively from the JWT claims.
 *
 * <h3>Role → Authority Mapping</h3>
 * <p>Spring Security's {@code hasRole("ADMIN")} checks for an authority named
 * {@code "ROLE_ADMIN"}. This record prepends {@code "ROLE_"} automatically so
 * callers can use plain role names like {@code "USER"} or {@code "ADMIN"}.
 *
 * <h3>Stateless Design</h3>
 * <p>No database lookup is performed. Identity is fully derived from the token.
 * This is intentional — every ms saved here matters at 500 req/s.
 *
 * @param userId Authenticated user's identifier (from JWT {@code sub} claim).
 * @param role   User's role (from JWT {@code role} claim). E.g., "USER" or "ADMIN".
 */
public record UserPrincipal(String userId, String role) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Prepend "ROLE_" so Spring's hasRole("ADMIN") matches "ROLE_ADMIN"
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** No password in stateless JWT — always returns null. */
    @Override
    public String getPassword() {
        return null;
    }

    /** Returns the userId as the Spring Security username. */
    @Override
    public String getUsername() {
        return userId;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
