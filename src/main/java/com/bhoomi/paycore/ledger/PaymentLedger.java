package com.bhoomi.paycore.ledger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The money movements behind a card-style payment, built on TigerBeetle two-phase transfers.
 *
 * <p>A payment of {@code amount} with a platform {@code fee} has two legs:
 * customer -> merchant ({@code amount - fee}) and customer -> platform fee account ({@code fee}).
 * Both legs are always submitted as one linked chain, so they succeed or fail together.
 *
 * <p>Every transfer id is derived deterministically from the payment id and the step
 * (e.g. "capture:merchant"). Retrying any step therefore re-sends the same ids and
 * TigerBeetle answers Exists instead of moving money twice. This is what lets the
 * payments layer recover safely after a crash between Postgres and the ledger.
 */
@Service
public class PaymentLedger {

    /** An authorization hold is released automatically if not captured within 7 days. */
    static final int AUTHORIZATION_TTL_SECONDS = 7 * 24 * 60 * 60;

    private final LedgerService ledger;

    public PaymentLedger(LedgerService ledger) {
        this.ledger = ledger;
    }

    /** Holds the customer's money. Throws InsufficientFundsException if they cannot afford it. */
    public void authorize(UUID paymentId, UUID customerAccountId, UUID merchantAccountId,
                          long amount, long fee, Currency currency) {
        List<LedgerTransfer> legs = new ArrayList<>();
        legs.add(LedgerTransfer.pending(transferId(paymentId, "authorize", "merchant"),
                customerAccountId, merchantAccountId, amount - fee, TransferCode.PAYMENT,
                AUTHORIZATION_TTL_SECONDS));
        if (fee > 0) {
            legs.add(LedgerTransfer.pending(transferId(paymentId, "authorize", "fee"),
                    customerAccountId, SystemAccounts.platformFee(currency), fee, TransferCode.PLATFORM_FEE,
                    AUTHORIZATION_TTL_SECONDS));
        }
        ledger.submit(currency, legs);
    }

    /** Moves the held money to the merchant and the platform. */
    public void capture(UUID paymentId, long fee, Currency currency) {
        List<LedgerTransfer> legs = new ArrayList<>();
        legs.add(LedgerTransfer.postPending(transferId(paymentId, "capture", "merchant"),
                transferId(paymentId, "authorize", "merchant"), TransferCode.PAYMENT));
        if (fee > 0) {
            legs.add(LedgerTransfer.postPending(transferId(paymentId, "capture", "fee"),
                    transferId(paymentId, "authorize", "fee"), TransferCode.PLATFORM_FEE));
        }
        ledger.submit(currency, legs);
    }

    /** Releases the hold; no money moves. */
    public void cancel(UUID paymentId, long fee, Currency currency) {
        List<LedgerTransfer> legs = new ArrayList<>();
        legs.add(LedgerTransfer.voidPending(transferId(paymentId, "cancel", "merchant"),
                transferId(paymentId, "authorize", "merchant"), TransferCode.PAYMENT));
        if (fee > 0) {
            legs.add(LedgerTransfer.voidPending(transferId(paymentId, "cancel", "fee"),
                    transferId(paymentId, "authorize", "fee"), TransferCode.PLATFORM_FEE));
        }
        ledger.submit(currency, legs);
    }

    /** Returns a captured payment in full: the merchant's share and the platform fee go back to the customer. */
    public void refund(UUID paymentId, UUID customerAccountId, UUID merchantAccountId,
                       long amount, long fee, Currency currency) {
        List<LedgerTransfer> legs = new ArrayList<>();
        legs.add(LedgerTransfer.posted(transferId(paymentId, "refund", "merchant"),
                merchantAccountId, customerAccountId, amount - fee, TransferCode.REFUND));
        if (fee > 0) {
            legs.add(LedgerTransfer.posted(transferId(paymentId, "refund", "fee"),
                    SystemAccounts.platformFee(currency), customerAccountId, fee, TransferCode.REFUND));
        }
        ledger.submit(currency, legs);
    }

    /** Deterministic transfer id: the same payment, step and leg always give the same id. */
    static UUID transferId(UUID paymentId, String step, String leg) {
        String name = "paycore:" + paymentId + ":" + step + ":" + leg;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
