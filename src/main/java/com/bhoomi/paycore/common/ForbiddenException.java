package com.bhoomi.paycore.common;

/** The caller can see this resource but is not allowed to perform this action on it (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
