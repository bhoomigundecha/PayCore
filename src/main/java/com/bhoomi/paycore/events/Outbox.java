package com.bhoomi.paycore.events;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes events into the outbox table. It must run inside the caller's transaction (MANDATORY):
 * the event commits together with the state change it describes, or neither does. That is what
 * makes "update the database AND tell Kafka" safe without a distributed transaction.
 */
@Component
public class Outbox {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public Outbox(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Appends an event. {@code endpointId} limits delivery to one webhook endpoint (test events);
     * null means every endpoint of the merchant.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(UUID aggregateId, UUID merchantAccountId, UUID endpointId, String type,
                       Map<String, Object> data) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id.toString());
        envelope.put("type", type);
        envelope.put("createdAt", now.toString());
        envelope.put("data", data);
        jdbc.update("""
                insert into outbox_events (id, aggregate_id, merchant_account_id, endpoint_id, event_type, payload, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, aggregateId, merchantAccountId, endpointId, type, json.writeValueAsString(envelope),
                Timestamp.from(now));
        return id;
    }
}
