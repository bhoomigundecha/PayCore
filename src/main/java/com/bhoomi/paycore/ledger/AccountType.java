package com.bhoomi.paycore.ledger;

public enum AccountType {
    EXTERNAL_FUNDING(1, false),
    CUSTOMER_WALLET(2, true),
    MERCHANT(3, true),
    PLATFORM_FEE(4, true);

    private final int code;
    private final boolean cannotGoNegative;

    AccountType(int code, boolean cannotGoNegative) {
        this.code = code;
        this.cannotGoNegative = cannotGoNegative;
    }

    public int getCode() {
        return code;
    }

    public boolean cannotGoNegative() {
        return cannotGoNegative;
    }

    public boolean isSystemAccount() {
        return this == EXTERNAL_FUNDING || this == PLATFORM_FEE;
    }
}