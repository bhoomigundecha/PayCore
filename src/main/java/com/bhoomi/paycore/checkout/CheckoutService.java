package com.bhoomi.paycore.checkout;

import com.bhoomi.paycore.account.Account;
import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.card.Card;
import com.bhoomi.paycore.card.CardDeclinedException;
import com.bhoomi.paycore.card.CardVerifier;
import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.common.InvalidStateException;
import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.LedgerService;
import com.bhoomi.paycore.payment.Payment;
import com.bhoomi.paycore.payment.PaymentRepository;
import com.bhoomi.paycore.payment.PaymentService;
import com.bhoomi.paycore.payment.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Payment links. A merchant creates a session ("₹499 for a T-shirt"); anyone pays it on the hosted
 * checkout page by typing a PayCore card. The card is the payer's credential: it is verified like an
 * issuer would (Luhn, expiry, derived CVC, lockout), large amounts need the cardholder's one-time code
 * (3-D Secure style), and the database allows only one live payment per session.
 */
@Service
public class CheckoutService {

    private final CheckoutSessionRepository sessions;
    private final AccountService accounts;
    private final PaymentService paymentService;
    private final PaymentRepository payments;
    private final CardVerifier cardVerifier;

    public CheckoutService(CheckoutSessionRepository sessions, AccountService accounts, PaymentService paymentService,
                           PaymentRepository payments, CardVerifier cardVerifier) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.paymentService = paymentService;
        this.payments = payments;
        this.cardVerifier = cardVerifier;
    }

    public CheckoutSession create(UUID userId, UUID merchantAccountId, long amount, String description) {
        Account merchant = accounts.getOwned(merchantAccountId, userId);
        if (merchant.getType() != AccountType.MERCHANT) {
            throw new InvalidRequestException("Payment links can only be created for a store (MERCHANT account)");
        }
        return sessions.save(new CheckoutSession(LedgerService.newId(), merchant.getId(), userId, amount,
                merchant.getCurrency(), description.trim()));
    }

    public List<CheckoutSession> listFor(UUID userId) {
        return sessions.findTop20ByCreatedByUserIdOrderByCreatedAtDesc(userId);
    }

    public CheckoutSession get(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Payment link not found"));
    }

    public String merchantName(CheckoutSession session) {
        return accounts.get(session.getMerchantAccountId()).getName();
    }

    public Status statusOf(CheckoutSession session) {
        Optional<Payment> live = livePayment(session);
        if (live.isPresent()) {
            return switch (live.get().getStatus()) {
                case REQUIRES_AUTHENTICATION -> Status.AWAITING_APPROVAL;
                case REFUNDED, REFUNDING -> Status.REFUNDED;
                default -> Status.PAID;
            };
        }
        return session.isExpired() ? Status.EXPIRED : Status.OPEN;
    }

    /**
     * Pays a session by card. {@code attemptKey} is the checkout page's Idempotency-Key for this
     * attempt, so a double-clicked Pay button never creates two payments.
     */
    public PayResult pay(UUID sessionId, String number, int expMonth, int expYear, String cvc, String attemptKey) {
        CheckoutSession session = get(sessionId);
        Status status = statusOf(session);
        if (status == Status.EXPIRED) {
            throw new InvalidStateException("This payment link has expired");
        }
        if (status == Status.PAID || status == Status.REFUNDED) {
            throw new InvalidStateException("This payment link has already been paid");
        }

        Card card = cardVerifier.verify(number, expMonth, expYear, cvc);
        String key = IdempotencyKeys.scoped(card.getUserId(), "checkout:" + sessionId + ":" + IdempotencyKeys.validate(attemptKey));
        Payment payment = paymentService.createForCheckout(card.getUserId(), card.getAccountId(), key,
                session.getMerchantAccountId(), session.getAmount(), session.getId());
        return result(payment, card.getLast4());
    }

    /** The cardholder's one-time code for a large checkout payment. */
    public PayResult authenticate(UUID sessionId, UUID paymentId, String code) {
        get(sessionId);
        Payment payment = paymentService.authenticateCheckout(paymentId, sessionId, code);
        return result(payment, null);
    }

    private PayResult result(Payment payment, String last4) {
        return switch (payment.getStatus()) {
            case REQUIRES_AUTHENTICATION -> new PayResult(Outcome.REQUIRES_ACTION, null, payment.getId(), last4);
            case FAILED -> {
                String reason = payment.getFailureReason();
                if (reason != null && reason.startsWith("authentication")) {
                    throw new CardDeclinedException("authentication_failed", "The payment was not approved.");
                }
                throw new CardDeclinedException("insufficient_funds", "The card has insufficient funds.");
            }
            default -> new PayResult(Outcome.APPROVED, null, payment.getId(), last4);
        };
    }

    private Optional<Payment> livePayment(CheckoutSession session) {
        return payments.findFirstByCheckoutSessionIdAndStatusNot(session.getId(), PaymentStatus.FAILED);
    }

    public enum Status { OPEN, AWAITING_APPROVAL, PAID, REFUNDED, EXPIRED }

    public enum Outcome { APPROVED, REQUIRES_ACTION }

    public record PayResult(Outcome outcome, String declineCode, UUID paymentId, String cardLast4) {
    }
}
