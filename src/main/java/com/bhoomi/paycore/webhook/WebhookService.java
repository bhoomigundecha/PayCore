package com.bhoomi.paycore.webhook;

import com.bhoomi.paycore.account.Account;
import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.card.CardCrypto;
import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.events.Outbox;
import com.bhoomi.paycore.ledger.AccountType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Merchants register endpoints for their stores and look at what was delivered. */
@Service
public class WebhookService {

    static final int MAX_ENDPOINTS_PER_ACCOUNT = 3;

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final CardCrypto crypto; // the same AES-GCM vault that protects card numbers
    private final UrlGuard urlGuard;
    private final Outbox outbox;
    private final TransactionTemplate tx;

    public WebhookService(JdbcTemplate jdbc, AccountService accounts, CardCrypto crypto, UrlGuard urlGuard,
                          Outbox outbox, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.crypto = crypto;
        this.urlGuard = urlGuard;
        this.outbox = outbox;
        this.tx = tx;
    }

    public record Endpoint(UUID id, UUID accountId, String url, boolean testReceiver, Instant createdAt) {
    }

    /** The signing secret is returned only here, once. Afterwards it is only stored encrypted. */
    public record Created(Endpoint endpoint, String secret) {
    }

    public record Delivery(UUID id, UUID eventId, String eventType, String status, int attempts,
                           Instant nextAttemptAt, Integer lastStatusCode, String lastError,
                           Instant createdAt, Instant deliveredAt, String payload) {
    }

    public Created create(UUID userId, UUID accountId, String url, boolean testReceiver) {
        Account account = accounts.getOwned(accountId, userId);
        if (account.getType() != AccountType.MERCHANT) {
            throw new InvalidRequestException("Webhooks are for MERCHANT accounts");
        }
        Integer existing = jdbc.queryForObject("select count(*) from webhook_endpoints where account_id = ?",
                Integer.class, accountId);
        if (existing != null && existing >= MAX_ENDPOINTS_PER_ACCOUNT) {
            throw new InvalidRequestException("A store can have at most " + MAX_ENDPOINTS_PER_ACCOUNT + " webhook endpoints");
        }
        UUID id = UUID.randomUUID();
        String target;
        if (testReceiver) {
            target = TestReceiver.PATH + id;
        } else {
            if (url == null || url.isBlank() || url.length() > 500) {
                throw new InvalidRequestException("url is required (up to 500 characters)");
            }
            target = urlGuard.check(url).toString();
        }
        String secret = WebhookSigner.newSecret();
        Instant now = Instant.now();
        jdbc.update("""
                insert into webhook_endpoints (id, account_id, url, test_receiver, secret_ciphertext, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, id, accountId, target, testReceiver, crypto.encrypt(secret), Timestamp.from(now));
        return new Created(new Endpoint(id, accountId, target, testReceiver, now), secret);
    }

    /** Endpoints of all the user's stores. */
    public List<Endpoint> list(UUID userId) {
        List<UUID> mine = accounts.listOwned(userId).stream().map(Account::getId).toList();
        if (mine.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", mine.stream().map(x -> "?").toList());
        return jdbc.query("select id, account_id, url, test_receiver, created_at from webhook_endpoints where account_id in ("
                + in + ") order by created_at", (rs, i) -> new Endpoint(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4), rs.getTimestamp(5).toInstant()),
                mine.toArray());
    }

    public Endpoint requireOwned(UUID endpointId, UUID userId) {
        Endpoint e = find(endpointId);
        if (e == null || !accounts.get(e.accountId()).isOwnedBy(userId)) {
            throw new NotFoundException("Webhook endpoint not found: " + endpointId);
        }
        return e;
    }

    Endpoint find(UUID endpointId) {
        return jdbc.query("select id, account_id, url, test_receiver, created_at from webhook_endpoints where id = ?",
                (rs, i) -> new Endpoint(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getBoolean(4), rs.getTimestamp(5).toInstant()), endpointId).stream().findFirst().orElse(null);
    }

    String secretOf(UUID endpointId) {
        return crypto.decrypt(jdbc.queryForObject("select secret_ciphertext from webhook_endpoints where id = ?",
                String.class, endpointId));
    }

    public void delete(UUID endpointId, UUID userId) {
        requireOwned(endpointId, userId);
        jdbc.update("delete from webhook_endpoints where id = ?", endpointId); // deliveries cascade
    }

    public List<Delivery> deliveries(UUID endpointId, UUID userId) {
        requireOwned(endpointId, userId);
        return jdbc.query("""
                select id, event_id, event_type, status, attempts, next_attempt_at, last_status_code, last_error,
                       created_at, delivered_at, payload
                from webhook_deliveries where endpoint_id = ? order by created_at desc limit 50
                """, (rs, i) -> new Delivery(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getInt(5), instant(rs.getTimestamp(6)),
                (Integer) rs.getObject(7), rs.getString(8), rs.getTimestamp(9).toInstant(),
                instant(rs.getTimestamp(10)), rs.getString(11)), endpointId);
    }

    /** Sends a {@code webhook.test} event through the whole pipeline: outbox, Kafka, consumer, delivery. */
    public UUID sendTest(UUID endpointId, UUID userId) {
        Endpoint e = requireOwned(endpointId, userId);
        return tx.execute(status -> outbox.append(e.id(), e.accountId(), e.id(), "webhook.test",
                Map.of("object", "test", "message", "Hello from PayCore", "endpointId", e.id().toString())));
    }

    /** Try a delivery again now (a merchant fixed their server, say). */
    public void retry(UUID deliveryId, UUID userId) {
        UUID endpointId = jdbc.query("select endpoint_id from webhook_deliveries where id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), deliveryId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Delivery not found: " + deliveryId));
        requireOwned(endpointId, userId);
        jdbc.update("update webhook_deliveries set status = 'PENDING', next_attempt_at = now(), locked_until = null "
                + "where id = ? and status <> 'DELIVERED'", deliveryId);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
