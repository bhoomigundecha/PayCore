package com.bhoomi.paycore.ledger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.tigerbeetle.Client;
import com.tigerbeetle.IdBatch;

@Component
public class TigerBeetleHealthIndicator implements HealthIndicator {

    private final Client client;

    public TigerBeetleHealthIndicator(Client client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            IdBatch ids = new IdBatch(1);
            ids.add(1L);
            client.lookupAccountsAsync(ids).get(2, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (TimeoutException | ExecutionException e) {
            return Health.down(e).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).build();
        }
    }
}