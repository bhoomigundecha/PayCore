package com.bhoomi.paycore.common;

public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    private static final int MAX_LENGTH = 255;

    private IdempotencyKeys() {
    }

    /**
     * Validates the key and scopes it to the user, so two users can never collide on (or probe)
     * each other's keys.
     */
    public static String scoped(java.util.UUID userId, String key) {
        return userId + ":" + validate(key);
    }

    public static String validate(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
            throw new InvalidRequestException(HEADER + " must be 1-" + MAX_LENGTH + " characters");
        }
        return key;
    }
}
