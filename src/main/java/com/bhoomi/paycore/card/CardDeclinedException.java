package com.bhoomi.paycore.card;

/**
 * A card payment was declined. {@code code} uses the same vocabulary as card networks and processors
 * (incorrect_number, expired_card, incorrect_cvc, insufficient_funds, ...). Returned as HTTP 402.
 */
public class CardDeclinedException extends RuntimeException {

    private final String code;

    public CardDeclinedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
