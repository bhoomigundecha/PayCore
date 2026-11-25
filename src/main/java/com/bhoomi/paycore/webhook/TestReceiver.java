package com.bhoomi.paycore.webhook;

import com.bhoomi.paycore.common.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * A merchant server you don't have to build: a webhook endpoint inside PayCore that does exactly what a
 * real receiver should. It verifies the signature, rejects stale timestamps, notices duplicate event ids,
 * and keeps the last 20 requests so the web app can show them. It can also pretend to be down (503) to
 * show retries with backoff.
 */
@RestController
public class TestReceiver {

    public static final String PATH = "/webhooks/receiver/";
    private static final int KEEP = 20;
    private static final Duration TTL = Duration.ofDays(1);

    private final WebhookService webhooks;
    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public TestReceiver(WebhookService webhooks, StringRedisTemplate redis, JsonMapper json) {
        this.webhooks = webhooks;
        this.redis = redis;
        this.json = json;
    }

    public record Received(Instant at, String eventId, String eventType, int attempt, boolean signatureValid,
                           boolean duplicate, int responseStatus, String body) {
    }

    @PostMapping(PATH + "{endpointId}")
    public ResponseEntity<String> receive(@PathVariable UUID endpointId,
                                          @RequestHeader(value = WebhookSigner.HEADER, required = false) String signature,
                                          @RequestHeader(value = "PayCore-Event-Id", required = false) String eventId,
                                          @RequestHeader(value = "PayCore-Event-Type", required = false) String eventType,
                                          @RequestHeader(value = "PayCore-Delivery-Attempt", required = false) String attempt,
                                          @RequestBody String body) {
        WebhookService.Endpoint endpoint = webhooks.find(endpointId);
        if (endpoint == null || !endpoint.testReceiver()) {
            throw new NotFoundException("No test receiver here");
        }
        boolean valid = WebhookSigner.verify(webhooks.secretOf(endpointId), signature, body, Instant.now().getEpochSecond());
        boolean failing = Boolean.TRUE.equals(redis.hasKey(failingKey(endpointId)));
        int status = !valid ? 400 : failing ? 503 : 200;
        // A real receiver remembers processed event ids and skips repeats (deliveries are at-least-once).
        boolean duplicate = false;
        if (status == 200 && eventId != null) {
            Long added = redis.opsForSet().add(seenKey(endpointId), eventId);
            redis.expire(seenKey(endpointId), TTL);
            duplicate = added != null && added == 0;
        }
        record(endpointId, new Received(Instant.now(), eventId, eventType, parse(attempt), valid, duplicate, status, body));
        return switch (status) {
            case 200 -> ResponseEntity.ok(duplicate ? "duplicate, ignored" : "ok");
            case 400 -> ResponseEntity.badRequest().body("invalid signature");
            default -> ResponseEntity.status(503).body("simulated outage");
        };
    }

    public List<Received> recent(UUID endpointId) {
        List<String> raw = redis.opsForList().range(logKey(endpointId), 0, KEEP - 1);
        return raw == null ? List.of() : raw.stream().map(s -> json.readValue(s, Received.class)).toList();
    }

    public void setFailing(UUID endpointId, boolean failing) {
        if (failing) {
            redis.opsForValue().set(failingKey(endpointId), "1", TTL);
        } else {
            redis.delete(failingKey(endpointId));
        }
    }

    public boolean isFailing(UUID endpointId) {
        return Boolean.TRUE.equals(redis.hasKey(failingKey(endpointId)));
    }

    private void record(UUID endpointId, Received r) {
        String key = logKey(endpointId);
        redis.opsForList().leftPush(key, json.writeValueAsString(r));
        redis.opsForList().trim(key, 0, KEEP - 1);
        redis.expire(key, TTL);
    }

    private static int parse(String attempt) {
        try {
            return attempt == null ? 0 : Integer.parseInt(attempt);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String logKey(UUID id) {
        return "webhook-receiver:" + id + ":log";
    }

    private static String seenKey(UUID id) {
        return "webhook-receiver:" + id + ":seen";
    }

    private static String failingKey(UUID id) {
        return "webhook-receiver:" + id + ":failing";
    }
}
