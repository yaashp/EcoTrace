package com.ecotrace.domain;

import java.security.SecureRandom;

/**
 * Rules for the verified physical handover, kept free of framework code so they can be tested directly.
 * Codes come from a cryptographically secure random source. Photo types are decided from the file's
 * signature bytes, never from the name or the MIME type the browser claims.
 */
public final class HandoverRules {

    /** Digits in a collection code. */
    public static final int CODE_DIGITS = 6;

    /** Wrong guesses allowed for one code before it stops working. */
    public static final int MAX_ATTEMPTS = 5;

    /** Largest accepted photo. */
    public static final long MAX_PHOTO_BYTES = 3L * 1024 * 1024;

    private static final String REFERENCE_PREFIX = "HO-";
    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_LENGTH = 8;

    private HandoverRules() {
    }

    /** A uniformly random numeric code, zero-padded to CODE_DIGITS. */
    public static String newCode(SecureRandom random) {
        int bound = 1;
        for (int i = 0; i < CODE_DIGITS; i++) {
            bound *= 10;
        }
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
    }

    public static boolean isWellFormedCode(String raw) {
        return raw != null && raw.matches("\\d{" + CODE_DIGITS + "}");
    }

    /** A public handover reference such as HO-7KQ2M9XD. It is not derived from the code or any personal data. */
    public static String newReference(SecureRandom random) {
        StringBuilder sb = new StringBuilder(REFERENCE_PREFIX);
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            sb.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Returns the image type from the signature bytes (JPEG, PNG or WebP), or null for anything else.
     * Everything else, including SVG and HTML, is refused.
     */
    public static String detectImageType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (matchesAt(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (matchesAt(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (matchesAt(bytes, 0, 'R', 'I', 'F', 'F') && matchesAt(bytes, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean matchesAt(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
