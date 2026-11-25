package com.bhoomi.paycore.account;

import com.bhoomi.paycore.common.IdempotencyKeyReusedException;
import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.ledger.AccountBalance;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.ledger.LedgerService;
import com.bhoomi.paycore.ledger.LedgerTransfer;
import com.bhoomi.paycore.ledger.SystemAccounts;
import com.bhoomi.paycore.ledger.TransferCode;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TopUpRepository topUps;
    private final LedgerService ledger;

    public AccountService(AccountRepository accounts, TopUpRepository topUps, LedgerService ledger) {
        this.accounts = accounts;
        this.topUps = topUps;
        this.ledger = ledger;
    }

    /**
     * Creates the ledger account first, then the Postgres row. If we crash in between, the
     * ledger is left with an empty, unreferenced account, which is harmless.
     */
    public Account create(UUID ownerUserId, AccountType type, String name, Currency currency) {
        if (type.isSystemAccount()) {
            throw new InvalidRequestException("System accounts cannot be created via the API");
        }
        UUID id = ledger.createAccount(type, currency);
        return accounts.save(new Account(id, type, name, currency, ownerUserId));
    }

    /** Any account, regardless of owner. For internal checks (e.g. "is this a merchant?"). */
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * An account the given user owns. Someone else's account answers 404, not 403, so the API
     * doesn't reveal which account ids exist.
     */
    public Account getOwned(UUID id, UUID userId) {
        Account account = get(id);
        if (!account.isOwnedBy(userId)) {
            throw notFound(id);
        }
        return account;
    }

    public List<Account> listOwned(UUID userId) {
        return accounts.findByOwnerUserIdOrderByCreatedAtAsc(userId);
    }

    public AccountBalance balance(UUID id) {
        return ledger.getBalance(get(id).getId());
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Account not found: " + id);
    }

    /**
     * Adds money to an account exactly once per Idempotency-Key. The top-up row (with its
     * transfer id) is written before the ledger is touched, so a retry or the recovery job
     * can always finish it without crediting twice.
     */
    public TopUp topUp(String idempotencyKey, UUID accountId, long amount) {
        TopUp topUp = topUps.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> insertTopUp(idempotencyKey, accountId, amount));
        if (!topUp.sameRequestAs(accountId, amount)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        return completeTopUp(topUp);
    }

    /** Applies a PENDING top-up to the ledger and marks it COMPLETED. Safe to call repeatedly. */
    public TopUp completeTopUp(TopUp topUp) {
        if (topUp.getStatus() == TopUp.Status.COMPLETED) {
            return topUp;
        }
        ledger.submit(topUp.getCurrency(), LedgerTransfer.posted(topUp.getId(),
                SystemAccounts.externalFunding(topUp.getCurrency()), topUp.getAccountId(),
                topUp.getAmount(), TransferCode.TOP_UP));
        topUp.markCompleted();
        return topUps.save(topUp);
    }

    private TopUp insertTopUp(String idempotencyKey, UUID accountId, long amount) {
        Account account = get(accountId);
        try {
            return topUps.saveAndFlush(new TopUp(LedgerService.newId(), idempotencyKey, accountId, amount,
                    account.getCurrency()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key inserted first; use its row.
            return topUps.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        }
    }
}
