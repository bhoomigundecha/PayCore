package com.bhoomi.paycore.ledger;

import java.util.UUID;

/**
 * One transfer to submit to TigerBeetle. Use the factory methods; they make the
 * four kinds of transfer impossible to mix up.
 */
public record LedgerTransfer(
        UUID id,
        Kind kind,
        UUID debitAccountId,
        UUID creditAccountId,
        long amount,
        TransferCode code,
        UUID pendingId,
        int timeoutSeconds) {

    public enum Kind {
        /** Money moves immediately. */
        POSTED,
        /** Money is held (authorized) but not moved. */
        PENDING,
        /** A hold is completed: the held money moves. */
        POST_PENDING,
        /** A hold is cancelled: the held money is released. */
        VOID_PENDING
    }

    public static LedgerTransfer posted(UUID id, UUID from, UUID to, long amount, TransferCode code) {
        return new LedgerTransfer(id, Kind.POSTED, from, to, amount, code, null, 0);
    }

    public static LedgerTransfer pending(UUID id, UUID from, UUID to, long amount, TransferCode code,
                                         int timeoutSeconds) {
        return new LedgerTransfer(id, Kind.PENDING, from, to, amount, code, null, timeoutSeconds);
    }

    public static LedgerTransfer postPending(UUID id, UUID pendingId, TransferCode code) {
        return new LedgerTransfer(id, Kind.POST_PENDING, null, null, 0, code, pendingId, 0);
    }

    public static LedgerTransfer voidPending(UUID id, UUID pendingId, TransferCode code) {
        return new LedgerTransfer(id, Kind.VOID_PENDING, null, null, 0, code, pendingId, 0);
    }
}
