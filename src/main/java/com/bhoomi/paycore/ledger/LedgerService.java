package com.bhoomi.paycore.ledger;

import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.Client;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.UInt128;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private static final long TIMEOUT_SECONDS = 5;

    private final Client client;

    public LedgerService(Client client) {
        this.client = client;
    }

    public UUID createAccount(AccountType type, Currency currency) {
        byte[] id = UInt128.id();

        AccountBatch batch = new AccountBatch(1);
        batch.add();
        batch.setId(id);
        batch.setLedger(currency.getLedgerId());
        batch.setCode(type.getCode());
        batch.setFlags(type.cannotGoNegative()
                ? AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS
                : AccountFlags.NONE);

        CreateAccountResultBatch results;
        try {
            results = client.createAccountsAsync(batch).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new LedgerException("Ledger did not respond in time; outcome unknown", e);
        } catch (ExecutionException e) {
            throw new LedgerException("Ledger request failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LedgerException("Interrupted while waiting for ledger", e);
        }

        results.next();
        CreateAccountStatus status = results.getStatus();
        if (status != CreateAccountStatus.Created && status != CreateAccountStatus.Exists) {
            throw new LedgerException("Could not create account: " + status);
        }

        return UInt128.asUUID(id);
    }
}