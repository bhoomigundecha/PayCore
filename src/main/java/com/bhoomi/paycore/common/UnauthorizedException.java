package com.bhoomi.paycore.common;

/** Missing or wrong credentials (HTTP 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
