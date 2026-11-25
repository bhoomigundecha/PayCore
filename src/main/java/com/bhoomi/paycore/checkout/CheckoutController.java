package com.bhoomi.paycore.checkout;

import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/**
 * Creating payment links needs a session (you must own the store). Viewing and paying a link is public:
 * the card is the credential. Paying and approving are in the strict rate-limit bucket.
 */
@RestController
@RequestMapping("/checkout-sessions")
public class CheckoutController {

    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody CreateRequest body) {
        return view(checkout.create(me.id(), body.merchantAccountId(), body.amount(), body.description()));
    }

    /** Your payment links, newest first. */
    @GetMapping
    public List<SessionView> mine(@AuthenticationPrincipal CurrentUser me) {
        return checkout.listFor(me.id()).stream().map(this::view).toList();
    }

    /** Public: what the checkout page shows. No payer details. */
    @GetMapping("/{id}")
    public SessionView get(@PathVariable UUID id) {
        return view(checkout.get(id));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<CheckoutService.PayResult> pay(@PathVariable UUID id,
                                                          @RequestHeader(IdempotencyKeys.HEADER) String attemptKey,
                                                          @Valid @RequestBody PayRequest body) {
        CheckoutService.PayResult result = checkout.pay(id, body.number(), body.expMonth(), body.expYear(), body.cvc(), attemptKey);
        HttpStatus status = result.outcome() == CheckoutService.Outcome.REQUIRES_ACTION ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/{id}/authenticate")
    public CheckoutService.PayResult authenticate(@PathVariable UUID id, @Valid @RequestBody AuthenticateRequest body) {
        return checkout.authenticate(id, body.paymentId(), body.code());
    }

    private SessionView view(CheckoutSession s) {
        return new SessionView(s.getId(), checkout.merchantName(s), s.getDescription(), s.getAmount(), s.getCurrency(),
                checkout.statusOf(s), s.getCreatedAt(), s.getExpiresAt());
    }

    public record CreateRequest(@NotNull UUID merchantAccountId, @Positive long amount,
                                @NotBlank @Size(max = 200) String description) {
    }

    /** Card details are only used in memory to verify the card; they are never stored or logged. */
    public record PayRequest(@NotBlank @Size(max = 23) String number,
                             @Min(1) @Max(12) int expMonth,
                             @Min(0) @Max(2100) int expYear,
                             @NotBlank @Size(min = 3, max = 4) String cvc) {

        @Override
        public String toString() {
            return "PayRequest[card ending " + (number.length() >= 4 ? number.substring(number.length() - 4) : "") + "]";
        }
    }

    public record AuthenticateRequest(@NotNull UUID paymentId, @NotBlank @Size(max = 12) String code) {
    }

    public record SessionView(UUID id, String merchantName, String description, long amount, Currency currency,
                              CheckoutService.Status status, Instant createdAt, Instant expiresAt) {
    }
}
