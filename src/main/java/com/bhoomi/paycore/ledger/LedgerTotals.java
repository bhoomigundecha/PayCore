package com.bhoomi.paycore.ledger;

/**
 * Sums over every account on a ledger. Every transfer adds the same amount to one account's
 * debits and another's credits, so total debits must always equal total credits.
 */
public record LedgerTotals(
        Currency currency,
        long accounts,
        long debitsPosted,
        long creditsPosted,
        long debitsPending,
        long creditsPending) {

    public boolean balanced() {
        return debitsPosted == creditsPosted && debitsPending == creditsPending;
    }
}
