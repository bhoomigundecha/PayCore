package com.bhoomi.paycore.ledger;

public enum Currency {
    INR(356);

    private final int ledgerId;
    Currency(int ledgerId){
        this.ledgerId = ledgerId;
    }

    public int getLedgerId(){
        return ledgerId;
    }



}
