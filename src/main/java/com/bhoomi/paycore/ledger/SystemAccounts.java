package com.bhoomi.paycore.ledger;

import com.tigerbeetle.UInt128;
import java.util.UUID;

/**
 * Well-known ids of PayCore's own ledger accounts. There is exactly one of each per currency,
 * created at startup by {@link SystemAccountsInitializer}.
 */
public final class SystemAccounts {

    public static final UUID EXTERNAL_FUNDING_INR = UInt128.asUUID(UInt128.asBytes(1L));
    public static final UUID PLATFORM_FEE_INR = UInt128.asUUID(UInt128.asBytes(2L));

    private SystemAccounts() {
    }

    public static UUID externalFunding(Currency currency) {
        return switch (currency) {
            case INR -> EXTERNAL_FUNDING_INR;
        };
    }

    public static UUID platformFee(Currency currency) {
        return switch (currency) {
            case INR -> PLATFORM_FEE_INR;
        };
    }
}
