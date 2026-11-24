package com.bhoomi.paycore.common;

/** The operation is not allowed in the resource's current state (HTTP 409). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
