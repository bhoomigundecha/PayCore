package com.bhoomi.paycore.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends pending deliveries to merchants' servers, signed, with retries.
 *
 * <ul>
 *   <li>Claims due rows with {@code FOR UPDATE SKIP LOCKED} and a short lease ({@code locked_until}), so
 *       several instances share the work and a crashed worker's rows are picked up again.</li>
 *   <li>A 2xx answer is success. Anything else (error status, timeout, refused connection) is retried with
 *       exponential backoff plus jitter: about 10s, 20s, 40s, ... capped at 1 hour, up to 8 attempts.</li>
 *   <li>Merchants must treat deliveries as at-least-once and de-duplicate on {@code PayCore-Event-Id}.</li>
 * </ul>
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int BATCH = 20;

    private final JdbcTemplate jdbc;
    private final WebhookService webhooks;
    private final UrlGuard urlGuard;
    private final String selfBaseUrl;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER) // a redirect could point anywhere, including inside
            .build();
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();

    public WebhookDispatcher(JdbcTemplate jdbc, WebhookService webhooks, UrlGuard urlGuard,
                             @Value("${paycore.webhooks.self-base-url:http://localhost:8080}") String selfBaseUrl,
                             @Value("${paycore.webhooks.max-attempts:8}") int maxAttempts,
                             @Value("${paycore.webhooks.base-backoff:10s}") Duration baseBackoff,
                             @Value("${paycore.webhooks.max-backoff:1h}") Duration maxBackoff,
                             io.micrometer.core.instrument.MeterRegistry meters) {
        this.meters = meters;
        io.micrometer.core.instrument.Gauge.builder("paycore.webhooks.pending", jdbc, j -> {
            try {
                Long n = j.queryForObject("select count(*) from webhook_deliveries where status = 'PENDING'", Long.class);
                return n == null ? 0 : n;
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }).description("Webhook deliveries waiting for a (re)try").register(meters);
        this.jdbc = jdbc;
        this.webhooks = webhooks;
        this.urlGuard = urlGuard;
        this.selfBaseUrl = selfBaseUrl;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    private record Due(UUID id, UUID eventId, UUID endpointId, String eventType, String payload, int attempts,
                       String url, boolean testReceiver) {
    }

    @Scheduled(fixedDelayString = "${paycore.webhooks.interval:1s}")
    public void run() {
        try {
            List<Due> due = claim();
            var futures = due.stream().map(d -> senders.submit(() -> deliver(d))).toList();
            for (var f : futures) {
                f.get();
            }
        } catch (Exception e) {
            log.warn("Webhook dispatch round failed: {}", e.getMessage());
        }
    }

    /** Atomically leases up to BATCH due deliveries for 30 seconds. */
    List<Due> claim() {
        return jdbc.query("""
                with picked as (
                    select id from webhook_deliveries
                    where status = 'PENDING' and next_attempt_at <= now()
                      and (locked_until is null or locked_until < now())
                    order by next_attempt_at
                    limit ?
                    for update skip locked
                )
                update webhook_deliveries d set locked_until = now() + interval '30 seconds'
                from picked, webhook_endpoints e
                where d.id = picked.id and e.id = d.endpoint_id
                returning d.id, d.event_id, d.endpoint_id, d.event_type, d.payload, d.attempts, e.url, e.test_receiver
                """, (rs, i) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7),
                rs.getBoolean(8)), BATCH);
    }

    private void deliver(Due d) {
        int attempt = d.attempts() + 1;
        try {
            // The built-in receiver lives in this app; reach it directly. Any other URL is re-checked
            // against the SSRF rules, since its DNS may have changed since registration.
            URI target = d.testReceiver() ? URI.create(selfBaseUrl + d.url()) : urlGuard.check(d.url());
            long now = Instant.now().getEpochSecond();
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PayCore-Webhooks/1.0")
                    .header("PayCore-Event-Id", d.eventId().toString())
                    .header("PayCore-Event-Type", d.eventType())
                    .header("PayCore-Delivery-Attempt", Integer.toString(attempt))
                    .header(WebhookSigner.HEADER, WebhookSigner.header(webhooks.secretOf(d.endpointId()), now, d.payload()))
                    .POST(HttpRequest.BodyPublishers.ofString(d.payload()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                jdbc.update("""
                        update webhook_deliveries set status = 'DELIVERED', attempts = ?, last_status_code = ?,
                            last_error = null, delivered_at = now(), next_attempt_at = null, locked_until = null
                        where id = ?
                        """, attempt, code, d.id());
                meters.counter("paycore.webhooks.attempts", "result", "delivered").increment();
            } else {
                failed(d, attempt, code, "HTTP " + code + bodySnippet(response.body()));
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failed(d, attempt, null, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    private void failed(Due d, int attempt, Integer code, String error) {
        boolean giveUp = attempt >= maxAttempts;
        meters.counter("paycore.webhooks.attempts", "result", giveUp ? "gave_up" : "retry").increment();
        Instant next = giveUp ? null : Instant.now().plus(backoff(attempt));
        jdbc.update("""
                update webhook_deliveries set status = ?, attempts = ?, last_status_code = ?, last_error = ?,
                    next_attempt_at = ?, locked_until = null
                where id = ?
                """, giveUp ? "FAILED" : "PENDING", attempt, code, truncate(error),
                next == null ? null : Timestamp.from(next), d.id());
    }

    /** base * 2^(attempt-1), capped, then +-20% jitter so many failing deliveries don't retry in lockstep. */
    Duration backoff(int attempt) {
        long base = baseBackoff.toMillis();
        long millis = attempt >= 31 ? maxBackoff.toMillis() : Math.min(maxBackoff.toMillis(), base << (attempt - 1));
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis((long) (millis * jitter));
    }

    private static String bodySnippet(String body) {
        return body == null || body.isBlank() ? "" : ": " + body.strip();
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
