package com.bhoomi.paycore.common;

public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    private static final int MAX_LENGTH = 255;

    private IdempotencyKeys() {
    }

    public static String validate(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
            throw new InvalidRequestException(HEADER + " must be 1-" + MAX_LENGTH + " characters");
        }
        return key;
    }
}
