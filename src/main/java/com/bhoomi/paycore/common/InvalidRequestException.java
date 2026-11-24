package com.bhoomi.paycore.common;

/** The request is well-formed but not allowed (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
