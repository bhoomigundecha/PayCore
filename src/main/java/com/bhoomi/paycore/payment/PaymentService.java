package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.account.Account;
import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.common.ForbiddenException;
import com.bhoomi.paycore.common.IdempotencyKeyReusedException;
import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.common.InvalidStateException;
import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.InsufficientFundsException;
import com.bhoomi.paycore.ledger.LedgerConflictException;
import com.bhoomi.paycore.ledger.LedgerService;
import com.bhoomi.paycore.ledger.PaymentLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import com.bhoomi.paycore.stepup.StepUpService;
import com.bhoomi.paycore.stepup.WrongCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Payment state machine. Every step follows the same three moves that solve the dual-write problem:
 * <ol>
 *   <li>record the intent in Postgres (an "-ING" status),</li>
 *   <li>move the money in TigerBeetle with deterministic transfer ids,</li>
 *   <li>record the outcome in Postgres.</li>
 * </ol>
 * A crash between 2 and 3 leaves the payment in an "-ING" status; {@link #advance} finishes it,
 * and TigerBeetle answers Exists for anything already applied, so money never moves twice.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final AccountService accounts;
    private final PaymentLedger paymentLedger;
    private final FeePolicy feePolicy;
    private final StepUpService stepUps;

    public PaymentService(PaymentRepository payments, AccountService accounts,
                          PaymentLedger paymentLedger, FeePolicy feePolicy, StepUpService stepUps) {
        this.payments = payments;
        this.accounts = accounts;
        this.paymentLedger = paymentLedger;
        this.feePolicy = feePolicy;
        this.stepUps = stepUps;
    }

    /**
     * Creates a payment from one of the user's own wallets to any merchant.
     * {@code idempotencyKey} must already be scoped to the user.
     */
    public Payment create(UUID userId, String idempotencyKey, UUID customerId, UUID merchantId,
                          long amount, boolean autoCapture) {
        Account customer = accounts.getOwned(customerId, userId);
        return createFrom(customer, userId, idempotencyKey, merchantId, amount, autoCapture, null);
    }

    /**
     * A card payment for a checkout session. The card (number, expiry, CVC) was already verified by the
     * caller and is the payer's credential, so no session is required. Large amounts still need the
     * cardholder's one-time code (like 3-D Secure). The database allows only one live payment per session.
     */
    public Payment createForCheckout(UUID cardholderUserId, UUID walletAccountId, String idempotencyKey,
                                     UUID merchantId, long amount, UUID checkoutSessionId) {
        Account wallet = accounts.getOwned(walletAccountId, cardholderUserId);
        return createFrom(wallet, cardholderUserId, idempotencyKey, merchantId, amount, true, checkoutSessionId);
    }

    /** Approve a checkout payment with the cardholder's code; possession of the card was proven at checkout. */
    public Payment authenticateCheckout(UUID paymentId, UUID checkoutSessionId, String code) {
        Payment payment = get(paymentId);
        if (!checkoutSessionId.equals(payment.getCheckoutSessionId())) {
            throw new NotFoundException("Payment not found: " + paymentId);
        }
        UUID cardholder = accounts.get(payment.getCustomerAccountId()).getOwnerUserId();
        return authenticate(paymentId, cardholder, code);
    }

    private Payment createFrom(Account customer, UUID payerUserId, String idempotencyKey, UUID merchantId,
                               long amount, boolean autoCapture, UUID checkoutSessionId) {
        UUID customerId = customer.getId();
        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, customerId, merchantId, amount, autoCapture);
        }

        Account merchant = accounts.get(merchantId);
        if (customer.getType() != AccountType.CUSTOMER_WALLET) {
            throw new InvalidRequestException("customerAccountId must be a CUSTOMER_WALLET account");
        }
        if (merchant.getType() != AccountType.MERCHANT) {
            throw new InvalidRequestException("merchantAccountId must be a MERCHANT account");
        }
        if (customer.getCurrency() != merchant.getCurrency()) {
            throw new InvalidRequestException("Customer and merchant must use the same currency");
        }

        Payment payment = new Payment(LedgerService.newId(), idempotencyKey, customerId, merchantId,
                amount, feePolicy.feeFor(amount), customer.getCurrency(), autoCapture);
        payment.linkCheckout(checkoutSessionId);
        boolean needsApproval = stepUps.isRequired(amount);
        if (needsApproval) {
            payment.moveTo(PaymentStatus.REQUIRES_AUTHENTICATION); // the ledger is not touched until approved
        }
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            var winner = payments.findByIdempotencyKey(idempotencyKey);
            if (winner.isPresent()) {
                // Lost a race with a concurrent request using the same key.
                return replay(winner.get(), idempotencyKey, customerId, merchantId, amount, autoCapture);
            }
            if (checkoutSessionId != null) {
                // The partial unique index: another card already paid (or is paying) this checkout.
                throw new InvalidStateException("This payment link has already been paid");
            }
            throw e;
        }
        if (needsApproval) {
            stepUps.issue(challengeFor(payment, payerUserId, merchant));
            return payment;
        }
        return advance(payment);
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> notFound(id));
    }

    /** A payment the user is a party to, as the paying customer or the receiving merchant. */
    public Payment getVisibleTo(UUID id, UUID userId) {
        Payment payment = get(id);
        if (!isParty(payment, userId)) {
            throw notFound(id);
        }
        return payment;
    }

    /** Payments where any of the user's accounts is the customer or the merchant, newest first. */
    public List<Payment> listFor(UUID userId) {
        List<UUID> mine = accounts.listOwned(userId).stream().map(Account::getId).toList();
        return mine.isEmpty() ? List.of() : payments.findRecentForAccounts(mine, PageRequest.of(0, 50));
    }

    /**
     * The payer approves a large payment with the one-time code sent to their phone. Only then does
     * the payment enter the normal flow (AUTHORIZING) and touch the ledger.
     */
    public Payment authenticate(UUID id, UUID userId, String code) {
        Payment payment = requirePayer(id, userId);
        if (payment.getStatus() != PaymentStatus.REQUIRES_AUTHENTICATION) {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                throw new InvalidStateException("This payment can no longer be approved");
            }
            return advance(payment); // already approved, e.g. a double-submitted code
        }
        Account merchant = accounts.get(payment.getMerchantAccountId());
        StepUpService.Verification result = stepUps.verify(challengeFor(payment, userId, merchant), code);
        return switch (result.outcome()) {
            case APPROVED -> advance(save(payment, PaymentStatus.AUTHORIZING, null));
            case WRONG -> throw new WrongCodeException(result.attemptsLeft());
            case LOCKED -> save(payment, PaymentStatus.FAILED, "authentication_failed");
            case EXPIRED -> save(payment, PaymentStatus.FAILED, "authentication_expired");
        };
    }

    public void resendCode(UUID id, UUID userId) {
        Payment payment = requirePayer(id, userId);
        if (payment.getStatus() != PaymentStatus.REQUIRES_AUTHENTICATION) {
            throw new InvalidStateException("This payment is not waiting for a code");
        }
        stepUps.resend(challengeFor(payment, userId, accounts.get(payment.getMerchantAccountId())));
    }

    /** Fails payments whose code was never entered, so they don't wait forever. Returns how many. */
    public int expireUnapproved(java.time.Instant cutoff) {
        var stale = payments.findTop100ByStatusInAndUpdatedAtBefore(java.util.Set.of(PaymentStatus.REQUIRES_AUTHENTICATION), cutoff);
        for (Payment p : stale) {
            try {
                stepUps.clear(p.getId());
                save(p, PaymentStatus.FAILED, "authentication_expired");
            } catch (ObjectOptimisticLockingFailureException e) {
                // approved at the same moment; leave it
            }
        }
        return stale.size();
    }

    private Payment requirePayer(UUID paymentId, UUID userId) {
        Payment payment = getVisibleTo(paymentId, userId);
        if (!accounts.get(payment.getCustomerAccountId()).isOwnedBy(userId)) {
            throw new ForbiddenException("Only the payer can approve this payment");
        }
        return payment;
    }

    private static StepUpService.Challenge challengeFor(Payment p, UUID payerUserId, Account merchant) {
        String amount = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-IN"))
                .format(p.getAmount() / 100.0);
        return new StepUpService.Challenge(p.getId(), p.getAmount(), p.getMerchantAccountId(), payerUserId,
                amount, merchant.getName());
    }

    // Capture, release and refund are the merchant's decisions, as with card payments.

    public Payment capture(UUID id, UUID userId) {
        requireMerchantOwner(id, userId);
        return begin(id, PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURING, PaymentStatus.CAPTURED);
    }

    public Payment cancel(UUID id, UUID userId) {
        requireMerchantOwner(id, userId);
        return begin(id, PaymentStatus.AUTHORIZED, PaymentStatus.CANCELING, PaymentStatus.CANCELED);
    }

    public Payment refund(UUID id, UUID userId) {
        requireMerchantOwner(id, userId);
        return begin(id, PaymentStatus.CAPTURED, PaymentStatus.REFUNDING, PaymentStatus.REFUNDED);
    }

    private void requireMerchantOwner(UUID paymentId, UUID userId) {
        Payment payment = getVisibleTo(paymentId, userId);
        if (!accounts.get(payment.getMerchantAccountId()).isOwnedBy(userId)) {
            throw new ForbiddenException("Only the merchant can capture, release or refund this payment");
        }
    }

    private boolean isParty(Payment p, UUID userId) {
        return accounts.get(p.getCustomerAccountId()).isOwnedBy(userId)
                || accounts.get(p.getMerchantAccountId()).isOwnedBy(userId);
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Payment not found: " + id);
    }

    /**
     * Drives a payment out of an in-flight status. Safe to call repeatedly and concurrently
     * (from a client retry and the recovery job at the same time).
     */
    public Payment advance(Payment payment) {
        try {
            return switch (payment.getStatus()) {
                case AUTHORIZING -> finishAuthorize(payment);
                case CAPTURING -> finishCapture(payment);
                case CANCELING -> finishCancel(payment);
                case REFUNDING -> finishRefund(payment);
                default -> payment;
            };
        } catch (ObjectOptimisticLockingFailureException e) {
            // Someone else advanced this payment first; return what they wrote.
            return get(payment.getId());
        }
    }

    private Payment replay(Payment payment, String key, UUID customerId, UUID merchantId,
                           long amount, boolean autoCapture) {
        if (!payment.sameRequestAs(customerId, merchantId, amount, autoCapture)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return advance(payment);
    }

    private Payment begin(UUID id, PaymentStatus from, PaymentStatus inFlight, PaymentStatus done) {
        Payment payment = get(id);
        if (payment.getStatus() == done || payment.getStatus() == inFlight) {
            return advance(payment); // idempotent: repeating a finished or running step is fine
        }
        if (payment.getStatus() != from) {
            throw new InvalidStateException("Cannot move payment " + id + " from " + payment.getStatus()
                    + " to " + done);
        }
        payment.moveTo(inFlight);
        try {
            payment = payments.saveAndFlush(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            return advance(get(id));
        }
        return advance(payment);
    }

    private Payment finishAuthorize(Payment p) {
        try {
            paymentLedger.authorize(p.getId(), p.getCustomerAccountId(), p.getMerchantAccountId(),
                    p.getAmount(), p.getFee(), p.getCurrency());
        } catch (InsufficientFundsException e) {
            return save(p, PaymentStatus.FAILED, "insufficient_funds");
        } catch (LedgerConflictException e) {
            return save(p, PaymentStatus.FAILED, "declined");
        }
        if (p.isAutoCapture()) {
            return finishCapture(save(p, PaymentStatus.CAPTURING, null));
        }
        return save(p, PaymentStatus.AUTHORIZED, null);
    }

    private Payment finishCapture(Payment p) {
        try {
            paymentLedger.capture(p.getId(), p.getFee(), p.getCurrency());
        } catch (LedgerConflictException e) {
            log.warn("Capture of payment {} rejected by ledger: {}", p.getId(), e.getMessage());
            return save(p, PaymentStatus.CANCELED, "authorization_expired");
        }
        return save(p, PaymentStatus.CAPTURED, null);
    }

    private Payment finishCancel(Payment p) {
        try {
            paymentLedger.cancel(p.getId(), p.getFee(), p.getCurrency());
        } catch (LedgerConflictException e) {
            // The hold already expired, so the money is released either way.
            log.info("Cancel of payment {}: hold already released ({})", p.getId(), e.getMessage());
        }
        return save(p, PaymentStatus.CANCELED, null);
    }

    private Payment finishRefund(Payment p) {
        try {
            paymentLedger.refund(p.getId(), p.getCustomerAccountId(), p.getMerchantAccountId(),
                    p.getAmount(), p.getFee(), p.getCurrency());
        } catch (InsufficientFundsException e) {
            save(p, PaymentStatus.CAPTURED, "refund_failed_insufficient_merchant_balance");
            throw e;
        }
        return save(p, PaymentStatus.REFUNDED, null);
    }

    private Payment save(Payment p, PaymentStatus status, String reason) {
        p.moveTo(status, reason);
        return payments.saveAndFlush(p);
    }
}
