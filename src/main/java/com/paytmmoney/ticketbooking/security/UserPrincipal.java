package com.paytmmoney.ticketbooking.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Immutable security principal derived exclusively from the token identity.
 */
public record UserPrincipal(String userId, String role) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.isBlank()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        String normalized = role.toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return List.of(new SimpleGrantedAuthority(normalized));
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
