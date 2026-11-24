package com.bhoomi.paycore.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform fee in basis points (1 bp = 0.01%). 200 bps = 2%. Integer maths only: money is never a double.
 */
@Component
public class FeePolicy {

    private final long platformFeeBps;

    public FeePolicy(@Value("${paycore.fees.platform-bps:200}") long platformFeeBps) {
        if (platformFeeBps < 0 || platformFeeBps >= 10_000) {
            throw new IllegalArgumentException("platform-bps must be in [0, 10000)");
        }
        this.platformFeeBps = platformFeeBps;
    }

    /** Fee for an amount in the smallest currency unit, rounded down. */
    public long feeFor(long amount) {
        return Math.multiplyExact(amount, platformFeeBps) / 10_000;
    }
}
