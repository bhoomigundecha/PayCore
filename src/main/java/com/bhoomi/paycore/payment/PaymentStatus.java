package com.bhoomi.paycore.payment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment lifecycle. The "-ING" states are written to Postgres BEFORE the ledger is called;
 * they mean "this step was started but may not have finished". The recovery job finds
 * payments stuck in them and finishes the step, which is safe because ledger transfer ids
 * are deterministic.
 *
 * <pre>
 * AUTHORIZING -> AUTHORIZED -> CAPTURING -> CAPTURED -> REFUNDING -> REFUNDED
 *      |              |
 *      v              v
 *    FAILED       CANCELING -> CANCELED
 * </pre>
 */
public enum PaymentStatus {
    AUTHORIZING,
    AUTHORIZED,
    CAPTURING,
    CAPTURED,
    CANCELING,
    CANCELED,
    REFUNDING,
    REFUNDED,
    FAILED;

    public static final Set<PaymentStatus> IN_FLIGHT = EnumSet.of(AUTHORIZING, CAPTURING, CANCELING, REFUNDING);

    public boolean isInFlight() {
        return IN_FLIGHT.contains(this);
    }
}
