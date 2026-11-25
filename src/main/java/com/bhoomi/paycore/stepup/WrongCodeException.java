package com.bhoomi.paycore.stepup;

/** The one-time code was wrong but attempts remain (HTTP 422). */
public class WrongCodeException extends RuntimeException {

    private final int attemptsLeft;

    public WrongCodeException(int attemptsLeft) {
        super("That code is not right. " + attemptsLeft + (attemptsLeft == 1 ? " attempt" : " attempts") + " left.");
        this.attemptsLeft = attemptsLeft;
    }

    public int attemptsLeft() {
        return attemptsLeft;
    }
}
