package com.bhoomi.paycore.ledger;

import java.util.UUID;

/**
 * Balances of one ledger account, in the currency's smallest unit (paise for INR).
 * {@code available} excludes money on hold: credits posted - debits posted - debits pending.
 */
public record AccountBalance(
        UUID accountId,
        long available,
        long creditsPosted,
        long debitsPosted,
        long creditsPending,
        long debitsPending) {
}
