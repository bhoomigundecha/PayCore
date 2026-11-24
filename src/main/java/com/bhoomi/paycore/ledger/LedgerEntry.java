package com.bhoomi.paycore.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * One transfer as stored in TigerBeetle. For post/void transfers, TigerBeetle fills in the
 * accounts and amount from the pending transfer they complete.
 */
public record LedgerEntry(
        UUID id,
        LedgerTransfer.Kind kind,
        TransferCode code,
        UUID debitAccountId,
        UUID creditAccountId,
        long amount,
        UUID pendingId,
        Instant timestamp) {
}
