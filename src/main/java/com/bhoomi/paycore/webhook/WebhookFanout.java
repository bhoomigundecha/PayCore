package com.bhoomi.paycore.webhook;

import com.bhoomi.paycore.events.OutboxRelay;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer: turns each payment event into one pending delivery per webhook endpoint of the merchant.
 *
 * <p>Idempotent by construction. Kafka may hand us the same event twice (the relay re-sent it, or we
 * crashed before committing the offset); {@code unique (event_id, endpoint_id)} plus
 * {@code on conflict do nothing} makes the second time a no-op.
 */
@Component
public class WebhookFanout {

    private static final Logger log = LoggerFactory.getLogger(WebhookFanout.class);

    private final JdbcTemplate jdbc;

    public WebhookFanout(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "${paycore.events.topic}", groupId = "${paycore.events.consumer-group}")
    public void onEvent(ConsumerRecord<String, String> record) {
        UUID eventId = UUID.fromString(header(record, OutboxRelay.HEADER_EVENT_ID));
        String type = header(record, OutboxRelay.HEADER_EVENT_TYPE);
        UUID merchant = UUID.fromString(header(record, OutboxRelay.HEADER_MERCHANT));
        String endpoint = header(record, OutboxRelay.HEADER_ENDPOINT);

        String insert = """
                insert into webhook_deliveries (id, event_id, endpoint_id, event_type, payload, status, attempts, next_attempt_at, created_at)
                select gen_random_uuid(), ?, e.id, ?, ?, 'PENDING', 0, now(), now()
                from webhook_endpoints e
                where e.account_id = ?
                """;
        int created = endpoint == null
                ? jdbc.update(insert + " on conflict (event_id, endpoint_id) do nothing", eventId, type, record.value(), merchant)
                : jdbc.update(insert + " and e.id = ? on conflict (event_id, endpoint_id) do nothing", eventId, type,
                        record.value(), merchant, UUID.fromString(endpoint));
        if (created > 0) {
            log.debug("Event {} ({}) queued for {} endpoint(s)", eventId, type, created);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    @Configuration
    static class ErrorHandling {
        /**
         * If the database is down, keep retrying the same record (backing off up to 30s) instead of
         * skipping it, so no event is lost. A malformed record (bad header) can never succeed, so it is
         * logged and skipped rather than blocking its partition forever.
         */
        @Bean
        DefaultErrorHandler kafkaErrorHandler() {
            ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
            backOff.setMaxInterval(30_000);
            DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
            handler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
            return handler;
        }
    }
}
