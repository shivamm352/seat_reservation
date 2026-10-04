package com.paytmmoney.ticketbooking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Stateless JWT utility service.
 *
 * <h3>Token Structure</h3>
 * <pre>
 * Header : { "alg": "HS256" }
 * Payload: { "sub": "&lt;userId&gt;", "role": "&lt;role&gt;", "iat": &lt;epoch&gt;, "exp": &lt;epoch&gt; }
 * </pre>
 *
 * <h3>jjwt 0.12.x API Notes</h3>
 * <ul>
 *   <li>Builder: {@code Jwts.builder().subject(...).claim(...).signWith(key).compact()}</li>
 *   <li>Parser: {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token)}</li>
 *   <li>{@code signWith(SecretKey)} auto-selects HS256/HS384/HS512 based on key length.</li>
 * </ul>
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // ── Key ──────────────────────────────────────────────────────────────────

    /**
     * Derives the HMAC-SHA signing key from the configured secret string.
     * Secret must be at least 32 bytes (256 bits) for HS256.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Token Generation ─────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given user.
     *
     * @param userId The subject claim — used as the user's identity throughout the system.
     * @param role   User role (e.g., "USER" or "ADMIN"). Stored as custom {@code role} claim.
     * @return Compact, URL-safe JWT string.
     */
    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Validation ─────────────────────────────────────────────────────

    /**
     * Validates the token signature and expiry.
     *
     * <p>Never throws — returns {@code false} for any invalid token so the
     * filter can safely call this without a try-catch.
     *
     * @param token The compact JWT string.
     * @return {@code true} if the token is valid and not expired; {@code false} otherwise.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Claim Extraction ─────────────────────────────────────────────────────

    /**
     * Extracts the user ID from the {@code sub} claim.
     * Only call after {@link #validateToken(String)} returns {@code true}.
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the role from the custom {@code role} claim.
     * Only call after {@link #validateToken(String)} returns {@code true}.
     */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
