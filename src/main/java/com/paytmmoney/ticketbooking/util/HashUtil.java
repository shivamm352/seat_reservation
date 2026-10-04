package com.paytmmoney.ticketbooking.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic utility for generating deterministic request fingerprints.
 *
 * <p>Used by the reservation engine to compute a SHA-256 hash of the booking
 * request payload, enabling dual-check idempotency: same key + same hash = replay,
 * same key + different hash = conflict.
 */
public final class HashUtil {

    /** Non-instantiable utility class. */
    private HashUtil() {}

    /**
     * Computes the SHA-256 hash of the given input string.
     *
     * <p>Input is encoded as UTF-8 before hashing. The result is a lowercase
     * hex string of exactly 64 characters.
     *
     * <h3>Usage in Reservation Engine</h3>
     * <pre>
     * // Sort seats first to make hash order-invariant
     * Collections.sort(seatNumbers);
     * String hash = HashUtil.sha256(showId + ":" + String.join(",", seatNumbers));
     * // → "a3f8b2d1..." (same regardless of original seat order in request)
     * </pre>
     *
     * @param input The string to hash (must not be null).
     * @return Lowercase hex SHA-256 digest (64 chars).
     * @throws IllegalStateException if SHA-256 algorithm is unavailable (never in practice).
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec — this can never happen
            throw new IllegalStateException("SHA-256 algorithm not available on this JVM", e);
        }
    }
}
