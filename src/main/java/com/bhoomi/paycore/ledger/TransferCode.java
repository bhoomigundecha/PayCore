package com.bhoomi.paycore.ledger;

/**
 * Why money moved. Stored on every TigerBeetle transfer as its {@code code}.
 */
public enum TransferCode {
    TOP_UP(1),
    PAYMENT(2),
    PLATFORM_FEE(3),
    REFUND(4);

    private final int code;

    TransferCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
