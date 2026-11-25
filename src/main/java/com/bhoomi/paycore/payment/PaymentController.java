package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AccountService accountService;

    public PaymentController(PaymentService paymentService, AccountService accountService) {
        this.paymentService = paymentService;
        this.accountService = accountService;
    }

    private PaymentResponse view(Payment p) {
        return PaymentResponse.of(p, accountService.get(p.getCustomerAccountId()).getName(),
                accountService.get(p.getMerchantAccountId()).getName());
    }

    /**
     * Creates a payment. With {@code capture=true} (the default) money moves immediately;
     * with {@code capture=false} it is only authorized (held) until /capture or /cancel.
     * A declined payment is still recorded and returned with status FAILED and HTTP 422.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal CurrentUser me,
                                                  @RequestHeader(IdempotencyKeys.HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreatePaymentRequest request) {
        boolean autoCapture = request.capture() == null || request.capture();
        Payment payment = paymentService.create(me.id(), IdempotencyKeys.scoped(me.id(), idempotencyKey),
                request.customerAccountId(), request.merchantAccountId(), request.amount(), autoCapture);
        HttpStatus status = switch (payment.getStatus()) {
            case FAILED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case REQUIRES_AUTHENTICATION -> HttpStatus.ACCEPTED; // waiting for the payer's code
            default -> HttpStatus.CREATED;
        };
        return ResponseEntity.status(status).body(view(payment));
    }

    /** Your payments, as payer or as merchant, newest first. */
    @GetMapping
    public List<PaymentResponse> list(@AuthenticationPrincipal CurrentUser me) {
        return paymentService.listFor(me.id()).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return view(paymentService.getVisibleTo(id, me.id()));
    }

    /** Approve a large payment with the one-time code sent to the payer's phone. */
    @PostMapping("/{id}/authenticate")
    public PaymentResponse authenticate(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id,
                                        @Valid @RequestBody AuthenticateRequest request) {
        return view(paymentService.authenticate(id, me.id(), request.code()));
    }

    @PostMapping("/{id}/resend-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendCode(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        paymentService.resendCode(id, me.id());
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return view(paymentService.capture(id, me.id()));
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return view(paymentService.cancel(id, me.id()));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return view(paymentService.refund(id, me.id()));
    }

    public record AuthenticateRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record CreatePaymentRequest(@NotNull UUID customerAccountId,
                                       @NotNull UUID merchantAccountId,
                                       @Positive long amount,
                                       Boolean capture) {
    }

    public record PaymentResponse(UUID id, PaymentStatus status, long amount, long fee, Currency currency,
                                  UUID customerAccountId, String customerName,
                                  UUID merchantAccountId, String merchantName,
                                  String failureReason, Instant createdAt, Instant updatedAt) {
        static PaymentResponse of(Payment p, String customerName, String merchantName) {
            return new PaymentResponse(p.getId(), p.getStatus(), p.getAmount(), p.getFee(), p.getCurrency(),
                    p.getCustomerAccountId(), customerName, p.getMerchantAccountId(), merchantName,
                    p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
