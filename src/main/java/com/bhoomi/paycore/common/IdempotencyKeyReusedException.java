package com.bhoomi.paycore.common;

/** The same Idempotency-Key was sent with a different request body (HTTP 422). */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
