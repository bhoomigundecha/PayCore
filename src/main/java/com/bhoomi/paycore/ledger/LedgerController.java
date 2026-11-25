package com.bhoomi.paycore.ledger;

import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.user.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only views straight from the TigerBeetle ledger.
 */
@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private static final int MAX_TRANSFERS = 100;

    private final LedgerService ledger;
    private final AccountService accounts;

    public LedgerController(LedgerService ledger, AccountService accounts) {
        this.ledger = ledger;
        this.accounts = accounts;
    }

    @GetMapping("/overview")
    public LedgerOverview overview() {
        Currency currency = Currency.INR;
        AccountBalance funding = ledger.getBalance(SystemAccounts.externalFunding(currency));
        AccountBalance fees = ledger.getBalance(SystemAccounts.platformFee(currency));
        // External funding only ever sends money out, so its negative balance is the total that entered PayCore.
        return new LedgerOverview(currency, -funding.available(), fees.creditsPosted() - fees.debitsPosted(),
                fees.creditsPending(), SystemAccounts.externalFunding(currency), SystemAccounts.platformFee(currency));
    }

    /** Proof of double-entry: across all accounts, total debits equal total credits. */
    @GetMapping("/totals")
    public LedgerTotalsResponse totals() {
        LedgerTotals totals = ledger.totals(Currency.INR);
        return new LedgerTotalsResponse(totals.currency(), totals.accounts(), totals.debitsPosted(),
                totals.creditsPosted(), totals.debitsPending(), totals.creditsPending(), totals.balanced());
    }

    /** Transfer history of one of your accounts, or of PayCore's public system accounts. */
    @GetMapping("/accounts/{id}/transfers")
    public List<LedgerEntry> transfers(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id,
                                       @RequestParam(defaultValue = "50") int limit) {
        boolean system = id.equals(SystemAccounts.externalFunding(Currency.INR)) || id.equals(SystemAccounts.platformFee(Currency.INR));
        if (!system) {
            accounts.getOwned(id, me.id());
        }
        return ledger.getTransfers(id, Math.max(1, Math.min(limit, MAX_TRANSFERS)));
    }

    public record LedgerOverview(Currency currency, long totalMoneyIn, long platformRevenue,
                                 long platformRevenuePending, UUID externalFundingAccountId,
                                 UUID platformFeeAccountId) {
    }

    public record LedgerTotalsResponse(Currency currency, long accounts, long debitsPosted, long creditsPosted,
                                       long debitsPending, long creditsPending, boolean balanced) {
    }
}
