package com.paytmmoney.ticketbooking.controller;

import com.paytmmoney.ticketbooking.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Evaluation token generation endpoint.
 *
 * <h3>Purpose</h3>
 * <p>This controller exists to eliminate user-registration boilerplate during
 * load testing and evaluation. Burst scripts can call {@code POST /auth/token?userId=u{i}}
 * in a loop to instantly generate 500+ distinct authenticated identities.
 *
 * <h3>Usage</h3>
 * <pre>
 * # Regular user token
 * POST /auth/token?userId=alice
 * → { "token": "eyJ...", "type": "Bearer" }
 *
 * # Admin token (for creating shows)
 * POST /auth/token?userId=admin1&amp;role=ADMIN
 * → { "token": "eyJ...", "type": "Bearer" }
 *
 * # Use the token
 * Authorization: Bearer eyJ...
 * </pre>
 *
 * <p><strong>⚠ Production note:</strong> In a real system this endpoint would be
 * removed or secured. Here it is intentionally open per assignment requirements.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Generates a signed JWT for the specified user.
     *
     * @param userId The user ID to embed as the JWT {@code sub} claim.
     *               Becomes the identity used throughout the booking system.
     * @param role   Role to embed — defaults to {@code "USER"}.
     *               Use {@code "ADMIN"} to create shows.
     * @return JSON with {@code token} (signed JWT) and {@code type} ("Bearer").
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> generateToken(
            @RequestParam String userId,
            @RequestParam(defaultValue = "USER") String role
    ) {
        // Normalize role to uppercase so "admin", "Admin", "ADMIN" all work
        String normalizedRole = role.toUpperCase();
        String token = jwtService.generateToken(userId, normalizedRole);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "type",  "Bearer"
        ));
    }
}
