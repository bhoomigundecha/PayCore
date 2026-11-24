package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.account.Account;
import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.common.IdempotencyKeyReusedException;
import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.common.InvalidStateException;
import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.InsufficientFundsException;
import com.bhoomi.paycore.ledger.LedgerConflictException;
import com.bhoomi.paycore.ledger.LedgerService;
import com.bhoomi.paycore.ledger.PaymentLedger;
import java.util.UUID;
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

    public PaymentService(PaymentRepository payments, AccountService accounts,
                          PaymentLedger paymentLedger, FeePolicy feePolicy) {
        this.payments = payments;
        this.accounts = accounts;
        this.paymentLedger = paymentLedger;
        this.feePolicy = feePolicy;
    }

    public Payment create(String idempotencyKey, UUID customerId, UUID merchantId, long amount, boolean autoCapture) {
        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, customerId, merchantId, amount, autoCapture);
        }

        Account customer = accounts.get(customerId);
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
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent request using the same key.
            Payment winner = payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replay(winner, idempotencyKey, customerId, merchantId, amount, autoCapture);
        }
        return advance(payment);
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new NotFoundException("Payment not found: " + id));
    }

    public Payment capture(UUID id) {
        return begin(id, PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURING, PaymentStatus.CAPTURED);
    }

    public Payment cancel(UUID id) {
        return begin(id, PaymentStatus.AUTHORIZED, PaymentStatus.CANCELING, PaymentStatus.CANCELED);
    }

    public Payment refund(UUID id) {
        return begin(id, PaymentStatus.CAPTURED, PaymentStatus.REFUNDING, PaymentStatus.REFUNDED);
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
