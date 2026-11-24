package com.bhoomi.paycore.ledger;

/**
 * The ledger rejected an operation because of the state money is already in,
 * e.g. capturing a hold that was already voided or has expired.
 */
public class LedgerConflictException extends RuntimeException {

    public LedgerConflictException(String message) {
        super(message);
    }
}
