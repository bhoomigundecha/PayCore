package com.bhoomi.paycore.activity;

import com.bhoomi.paycore.account.TopUp;
import com.bhoomi.paycore.account.TopUpRepository;
import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.payment.Payment;
import com.bhoomi.paycore.payment.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous feed of what just happened on the ledger, for the landing page.
 * It exposes only the kind of event, amounts and time: no names, account ids or payment ids.
 */
@RestController
public class ActivityController {

    private static final int LIMIT = 12;
    private static final int MAX_FUNDED = 3;

    private final PaymentRepository payments;
    private final TopUpRepository topUps;

    public ActivityController(PaymentRepository payments, TopUpRepository topUps) {
        this.payments = payments;
        this.topUps = topUps;
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityEvent>> recent() {
        List<ActivityEvent> events = new ArrayList<>();
        for (Payment p : payments.findTop20ByOrderByUpdatedAtDesc()) {
            Kind kind = switch (p.getStatus()) {
                case CAPTURED -> Kind.CAPTURED;
                case AUTHORIZED -> Kind.AUTHORIZED;
                case CANCELED -> Kind.RELEASED;
                case REFUNDED -> Kind.REFUNDED;
                case FAILED -> Kind.DECLINED;
                default -> null; // in-flight states are not interesting to show
            };
            if (kind != null) {
                events.add(new ActivityEvent(key(p.getId(), kind), kind, p.getAmount(), p.getFee(), p.getCurrency(), p.getUpdatedAt()));
            }
        }
        // Every new user gets a welcome top-up, so cap these to keep the feed about payments.
        for (TopUp t : topUps.findTop10ByStatusOrderByUpdatedAtDesc(TopUp.Status.COMPLETED).stream().limit(MAX_FUNDED).toList()) {
            events.add(new ActivityEvent(key(t.getId(), Kind.FUNDED), Kind.FUNDED, t.getAmount(), 0, t.getCurrency(), t.getUpdatedAt()));
        }
        List<ActivityEvent> newest = events.stream()
                .sorted(Comparator.comparing(ActivityEvent::at).reversed())
                .limit(LIMIT)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(newest);
    }

    /** Stable, opaque key per event so the UI can animate new rows; doesn't reveal the real id. */
    private static String key(UUID id, Kind kind) {
        return UUID.nameUUIDFromBytes(("activity:" + id + ":" + kind).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public enum Kind { CAPTURED, AUTHORIZED, RELEASED, REFUNDED, DECLINED, FUNDED }

    public record ActivityEvent(String key, Kind kind, long amount, long fee, Currency currency, Instant at) {
    }
}
