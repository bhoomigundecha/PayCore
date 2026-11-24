package com.bhoomi.paycore.ledger;

import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFilter;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.Client;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.CreateTransferStatus;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.QueryFilter;
import com.tigerbeetle.TransferBatch;
import com.tigerbeetle.TransferFlags;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * Thin, typed wrapper around the TigerBeetle client. Knows nothing about payments;
 * it only creates accounts, submits transfers and reads balances.
 */
@Service
public class LedgerService {

    private static final long TIMEOUT_SECONDS = 5;

    private final Client client;

    public LedgerService(Client client) {
        this.client = client;
    }

    /** A new time-ordered 128-bit id, as TigerBeetle recommends. */
    public static UUID newId() {
        return UInt128.asUUID(UInt128.id());
    }

    public UUID createAccount(AccountType type, Currency currency) {
        return createAccount(newId(), type, currency);
    }

    public UUID createAccount(UUID id, AccountType type, Currency currency) {
        AccountBatch batch = new AccountBatch(1);
        batch.add();
        batch.setId(UInt128.asBytes(id));
        batch.setLedger(currency.getLedgerId());
        batch.setCode(type.getCode());
        batch.setFlags(type.cannotGoNegative()
                ? AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS
                : AccountFlags.NONE);

        CreateAccountResultBatch results = await(client.createAccountsAsync(batch));
        results.next();
        CreateAccountStatus status = results.getStatus();
        if (status != CreateAccountStatus.Created && status != CreateAccountStatus.Exists) {
            throw new LedgerException("Could not create account " + id + ": " + status);
        }
        return id;
    }

    /** Submits a single transfer. */
    public void submit(Currency currency, LedgerTransfer transfer) {
        submit(currency, List.of(transfer));
    }

    /**
     * Submits transfers as one linked chain: either every transfer succeeds or none do.
     * Retrying with the same transfer ids is safe: already-applied transfers report Exists.
     */
    public void submit(Currency currency, List<LedgerTransfer> transfers) {
        if (transfers.isEmpty()) {
            return;
        }
        TransferBatch batch = new TransferBatch(transfers.size());
        for (int i = 0; i < transfers.size(); i++) {
            boolean linkedToNext = i < transfers.size() - 1;
            addToBatch(batch, transfers.get(i), currency, linkedToNext);
        }

        CreateTransferResultBatch results = await(client.createTransfersAsync(batch));
        int index = 0;
        while (results.next()) {
            CreateTransferStatus status = results.getStatus();
            LedgerTransfer transfer = transfers.get(index++);
            switch (status) {
                case Created, Exists, LinkedEventFailed -> { }
                case ExceedsCredits -> throw new InsufficientFundsException(transfer.debitAccountId());
                case DebitAccountNotFound -> throw new AccountNotFoundException(transfer.debitAccountId());
                case CreditAccountNotFound -> throw new AccountNotFoundException(transfer.creditAccountId());
                case PendingTransferNotFound, PendingTransferAlreadyPosted, PendingTransferAlreadyVoided,
                     PendingTransferExpired, IdAlreadyFailed ->
                        throw new LedgerConflictException("Transfer " + transfer.id() + " rejected: " + status);
                default -> throw new LedgerException("Transfer " + transfer.id() + " rejected: " + status);
            }
        }
    }

    public AccountBalance getBalance(UUID accountId) {
        IdBatch ids = new IdBatch(1);
        ids.add(UInt128.asBytes(accountId));

        AccountBatch accounts = await(client.lookupAccountsAsync(ids));
        if (!accounts.next()) {
            throw new AccountNotFoundException(accountId);
        }

        long creditsPosted = accounts.getCreditsPosted().longValueExact();
        long debitsPosted = accounts.getDebitsPosted().longValueExact();
        long creditsPending = accounts.getCreditsPending().longValueExact();
        long debitsPending = accounts.getDebitsPending().longValueExact();
        long available = creditsPosted - debitsPosted - debitsPending;

        return new AccountBalance(accountId, available,
                creditsPosted, debitsPosted, creditsPending, debitsPending);
    }

    /** Most recent transfers touching an account, newest first. */
    public List<LedgerEntry> getTransfers(UUID accountId, int limit) {
        AccountFilter filter = new AccountFilter();
        filter.setAccountId(UInt128.asBytes(accountId));
        filter.setDebits(true);
        filter.setCredits(true);
        filter.setReversed(true);
        filter.setLimit(limit);

        TransferBatch transfers = await(client.getAccountTransfersAsync(filter));
        List<LedgerEntry> entries = new ArrayList<>(transfers.getLength());
        while (transfers.next()) {
            int flags = transfers.getFlags();
            LedgerTransfer.Kind kind = TransferFlags.hasPending(flags) ? LedgerTransfer.Kind.PENDING
                    : TransferFlags.hasPostPendingTransfer(flags) ? LedgerTransfer.Kind.POST_PENDING
                    : TransferFlags.hasVoidPendingTransfer(flags) ? LedgerTransfer.Kind.VOID_PENDING
                    : LedgerTransfer.Kind.POSTED;
            byte[] pendingId = transfers.getPendingId();
            entries.add(new LedgerEntry(
                    UInt128.asUUID(transfers.getId()),
                    kind,
                    TransferCode.fromCode(transfers.getCode()),
                    UInt128.asUUID(transfers.getDebitAccountId()),
                    UInt128.asUUID(transfers.getCreditAccountId()),
                    transfers.getAmount().longValueExact(),
                    isZero(pendingId) ? null : UInt128.asUUID(pendingId),
                    nanosToInstant(transfers.getTimestamp())));
        }
        return entries;
    }

    /** Sums balances over every account on the currency's ledger, paging through all of them. */
    public LedgerTotals totals(Currency currency) {
        final int pageSize = 1000;
        long accounts = 0;
        BigInteger debitsPosted = BigInteger.ZERO;
        BigInteger creditsPosted = BigInteger.ZERO;
        BigInteger debitsPending = BigInteger.ZERO;
        BigInteger creditsPending = BigInteger.ZERO;

        long timestampMin = 0;
        while (true) {
            QueryFilter filter = new QueryFilter();
            filter.setLedger(currency.getLedgerId());
            filter.setTimestampMin(timestampMin);
            filter.setLimit(pageSize);

            AccountBatch page = await(client.queryAccountsAsync(filter));
            long lastTimestamp = timestampMin;
            while (page.next()) {
                accounts++;
                debitsPosted = debitsPosted.add(page.getDebitsPosted());
                creditsPosted = creditsPosted.add(page.getCreditsPosted());
                debitsPending = debitsPending.add(page.getDebitsPending());
                creditsPending = creditsPending.add(page.getCreditsPending());
                lastTimestamp = page.getTimestamp();
            }
            if (page.getLength() < pageSize) {
                break;
            }
            timestampMin = lastTimestamp + 1;
        }
        return new LedgerTotals(currency, accounts, debitsPosted.longValueExact(), creditsPosted.longValueExact(),
                debitsPending.longValueExact(), creditsPending.longValueExact());
    }

    private static boolean isZero(byte[] id) {
        for (byte b : id) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static Instant nanosToInstant(long nanos) {
        return Instant.ofEpochSecond(0, nanos);
    }

    private static void addToBatch(TransferBatch batch, LedgerTransfer t, Currency currency, boolean linkedToNext) {
        batch.add();
        batch.setId(UInt128.asBytes(t.id()));
        batch.setLedger(currency.getLedgerId());
        batch.setCode(t.code().getCode());

        int flags = linkedToNext ? TransferFlags.LINKED : TransferFlags.NONE;
        switch (t.kind()) {
            case POSTED -> {
                batch.setDebitAccountId(UInt128.asBytes(t.debitAccountId()));
                batch.setCreditAccountId(UInt128.asBytes(t.creditAccountId()));
                batch.setAmount(t.amount());
            }
            case PENDING -> {
                batch.setDebitAccountId(UInt128.asBytes(t.debitAccountId()));
                batch.setCreditAccountId(UInt128.asBytes(t.creditAccountId()));
                batch.setAmount(t.amount());
                batch.setTimeout(t.timeoutSeconds());
                flags |= TransferFlags.PENDING;
            }
            case POST_PENDING -> {
                batch.setPendingId(UInt128.asBytes(t.pendingId()));
                batch.setAmount(TransferBatch.AMOUNT_MAX); // post the full held amount
                flags |= TransferFlags.POST_PENDING_TRANSFER;
            }
            case VOID_PENDING -> {
                batch.setPendingId(UInt128.asBytes(t.pendingId()));
                flags |= TransferFlags.VOID_PENDING_TRANSFER;
            }
        }
        batch.setFlags(flags);
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new LedgerException("Ledger did not respond in time; outcome unknown", e);
        } catch (ExecutionException e) {
            throw new LedgerException("Ledger request failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LedgerException("Interrupted while waiting for ledger", e);
        }
    }
}
