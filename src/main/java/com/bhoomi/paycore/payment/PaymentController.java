package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.ledger.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Creates a payment. With {@code capture=true} (the default) money moves immediately;
     * with {@code capture=false} it is only authorized (held) until /capture or /cancel.
     * A declined payment is still recorded and returned with status FAILED and HTTP 422.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader(IdempotencyKeys.HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreatePaymentRequest request) {
        boolean autoCapture = request.capture() == null || request.capture();
        Payment payment = paymentService.create(IdempotencyKeys.validate(idempotencyKey),
                request.customerAccountId(), request.merchantAccountId(), request.amount(), autoCapture);
        HttpStatus status = payment.getStatus() == PaymentStatus.FAILED
                ? HttpStatus.UNPROCESSABLE_CONTENT
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PaymentResponse.of(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.of(paymentService.get(id));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable UUID id) {
        return PaymentResponse.of(paymentService.capture(id));
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable UUID id) {
        return PaymentResponse.of(paymentService.cancel(id));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) {
        return PaymentResponse.of(paymentService.refund(id));
    }

    public record CreatePaymentRequest(@NotNull UUID customerAccountId,
                                       @NotNull UUID merchantAccountId,
                                       @Positive long amount,
                                       Boolean capture) {
    }

    public record PaymentResponse(UUID id, PaymentStatus status, long amount, long fee, Currency currency,
                                  UUID customerAccountId, UUID merchantAccountId, String failureReason,
                                  Instant createdAt, Instant updatedAt) {
        static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.getId(), p.getStatus(), p.getAmount(), p.getFee(), p.getCurrency(),
                    p.getCustomerAccountId(), p.getMerchantAccountId(), p.getFailureReason(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
