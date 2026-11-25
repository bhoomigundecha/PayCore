package com.bhoomi.paycore.events;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves committed outbox rows onto Kafka.
 *
 * <p>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several app instances can run the relay
 * at once without publishing the same row twice in parallel. A row is marked published only after
 * Kafka acknowledged it (acks=all). If the app dies between the ack and the update, the row is sent
 * again: delivery is at-least-once, and the consumer is idempotent.
 *
 * <p>The record key is the payment id, so all events of one payment land on the same partition and
 * keep their order.
 */
@Component
public class OutboxRelay {

    public static final String HEADER_EVENT_ID = "paycore-event-id";
    public static final String HEADER_EVENT_TYPE = "paycore-event-type";
    public static final String HEADER_MERCHANT = "paycore-merchant-account";
    public static final String HEADER_ENDPOINT = "paycore-endpoint";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private volatile boolean lastRunFailed;

    private final io.micrometer.core.instrument.MeterRegistry meters;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                       @Value("${paycore.events.topic}") String topic, io.micrometer.core.instrument.MeterRegistry meters) {
        this.meters = meters;
        // Events committed but not yet on Kafka. Grows while Kafka is down; should drain to 0 after.
        io.micrometer.core.instrument.Gauge.builder("paycore.outbox.backlog", jdbc, j -> {
            try {
                Long n = j.queryForObject("select count(*) from outbox_events where published_at is null", Long.class);
                return n == null ? 0 : n;
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }).description("Outbox events not yet published to Kafka").register(meters);
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.topic = topic;
    }

    private record Row(UUID id, UUID aggregateId, UUID merchant, UUID endpoint, String type, String payload,
                       Instant createdAt) {
    }

    @Scheduled(fixedDelayString = "${paycore.events.relay-interval:500ms}")
    public void run() {
        try {
            int sent;
            do {
                sent = publishBatch();
            } while (sent == BATCH);
            if (lastRunFailed) {
                log.info("Outbox relay recovered; Kafka is reachable again");
                lastRunFailed = false;
            }
        } catch (RuntimeException e) {
            if (!lastRunFailed) { // log once per outage, not every half second
                log.warn("Outbox relay could not publish to Kafka; will keep retrying: {}", e.getMessage());
            }
            lastRunFailed = true;
        }
    }

    /** Publishes one batch and returns how many rows it published. */
    public int publishBatch() {
        Integer count = tx.execute(status -> {
            List<Row> rows = jdbc.query("""
                    select id, aggregate_id, merchant_account_id, endpoint_id, event_type, payload, created_at
                    from outbox_events
                    where published_at is null
                    order by created_at
                    limit ?
                    for update skip locked
                    """, (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant()), BATCH);
            if (rows.isEmpty()) {
                return 0;
            }
            List<CompletableFuture<?>> acks = new ArrayList<>(rows.size());
            for (Row row : rows) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, row.aggregateId().toString(), row.payload());
                record.headers().add(HEADER_EVENT_ID, bytes(row.id()));
                record.headers().add(HEADER_EVENT_TYPE, row.type().getBytes());
                record.headers().add(HEADER_MERCHANT, bytes(row.merchant()));
                if (row.endpoint() != null) {
                    record.headers().add(HEADER_ENDPOINT, bytes(row.endpoint()));
                }
                acks.add(kafka.send(record));
            }
            try {
                CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Roll back: nothing is marked published, and the whole batch is retried next run.
                throw new IllegalStateException("Kafka did not acknowledge the batch: " + e.getMessage(), e);
            }
            Instant published = Instant.now();
            Timestamp now = Timestamp.from(published);
            // How long an event waited between commit and Kafka.
            var lag = meters.timer("paycore.outbox.publish.lag");
            rows.forEach(r -> lag.record(java.time.Duration.between(r.createdAt(), published)));
            jdbc.batchUpdate("update outbox_events set published_at = ? where id = ?",
                    rows.stream().map(r -> new Object[] {now, r.id()}).toList());
            return rows.size();
        });
        return count == null ? 0 : count;
    }

    private static byte[] bytes(UUID id) {
        return id.toString().getBytes();
    }
}
