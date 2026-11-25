package com.bhoomi.paycore.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.events.Outbox;
import com.bhoomi.paycore.events.OutboxRelay;
import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The whole pipeline against real Postgres and Kafka: payment change → outbox row (same transaction) →
 * relay → Kafka → consumer → delivery → signed HTTP POST, with retries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "server.port=18089")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Outbox outbox;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    WebhookFanout fanout;

    TestSessions.SignedIn shop;
    String wallet;
    String store;

    @BeforeEach
    void setUp() throws Exception {
        shop = TestSessions.guest(mvc); // a wallet with ₹5,000 and a store
        String accounts = mvc.perform(shop.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        wallet = JsonPath.<List<String>>read(accounts, "$[?(@.type == 'CUSTOMER_WALLET')].id").getFirst();
        store = JsonPath.<List<String>>read(accounts, "$[?(@.type == 'MERCHANT')].id").getFirst();
    }

    @Test
    void everyStatusChangeWritesExactlyOneEventInTheSameTransaction() throws Exception {
        String payment = pay(30_000, false);
        mvc.perform(shop.as(post("/payments/{id}/capture", payment))).andExpect(status().isOk());
        mvc.perform(shop.as(post("/payments/{id}/refund", payment))).andExpect(status().isOk());
        // Repeating a finished step changes nothing, so it must not announce anything either.
        mvc.perform(shop.as(post("/payments/{id}/capture", payment))).andExpect(status().isConflict());

        List<String> types = jdbc.queryForList(
                "select event_type from outbox_events where aggregate_id = ? order by created_at", String.class,
                UUID.fromString(payment));
        assertThat(types).containsExactly("payment.authorized", "payment.succeeded", "payment.refunded");
    }

    @Test
    void anEventRollsBackWithItsTransactionAndCannotBeWrittenOutsideOne() {
        UUID aggregate = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            outbox.append(aggregate, UUID.fromString(store), null, "payment.succeeded", Map.of());
            status.setRollbackOnly(); // the business change failed
        });
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, aggregate))
                .isZero();
        assertThatThrownBy(() -> outbox.append(aggregate, UUID.fromString(store), null, "payment.succeeded", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void paymentEventsReachTheTestReceiverSignedAndOnlyOnce() throws Exception {
        String endpoint = createTestReceiver();
        String payment = pay(25_000, true);

        await().ignoreExceptions().atMost(WAIT).untilAsserted(() -> assertThat(received(endpoint))
                .anySatisfy(r -> {
                    assertThat(r.get("eventType")).isEqualTo("payment.succeeded");
                    assertThat(r.get("signatureValid")).isEqualTo(true);
                    assertThat(r.get("responseStatus")).isEqualTo(200);
                    assertThat((String) r.get("body")).contains(payment);
                }));
        String deliveries = mvc.perform(shop.as(get("/webhooks/endpoints/{id}/deliveries", endpoint)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(deliveries, "$[*].status")).containsOnly("DELIVERED");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and published_at is null",
                Integer.class, UUID.fromString(payment))).isZero();
    }

    @Test
    void aDuplicateKafkaMessageStillMakesOneDelivery() throws Exception {
        String endpoint = createTestReceiver();
        UUID eventId = UUID.randomUUID();
        var record = new ConsumerRecord<>("topic", 0, 0L, "key", "{\"id\":\"" + eventId + "\"}");
        record.headers().add(OutboxRelay.HEADER_EVENT_ID, eventId.toString().getBytes());
        record.headers().add(OutboxRelay.HEADER_EVENT_TYPE, "payment.succeeded".getBytes());
        record.headers().add(OutboxRelay.HEADER_MERCHANT, store.getBytes());

        fanout.onEvent(record);
        fanout.onEvent(record); // Kafka redelivered it

        assertThat(jdbc.queryForObject("select count(*) from webhook_deliveries where event_id = ? and endpoint_id = ?",
                Integer.class, eventId, UUID.fromString(endpoint))).isOne();
    }

    @Test
    void anOutageIsRetriedWithBackoffUntilTheReceiverRecovers() throws Exception {
        String endpoint = createTestReceiver();
        mvc.perform(shop.as(put("/webhooks/endpoints/{id}/receiver", endpoint)
                .contentType(MediaType.APPLICATION_JSON).content("{\"failing\":true}"))).andExpect(status().isOk());
        sendTest(endpoint);

        await().ignoreExceptions().atMost(WAIT).untilAsserted(() -> {
            Map<String, Object> d = onlyDelivery(endpoint);
            assertThat(d.get("status")).isEqualTo("PENDING");
            assertThat((Integer) d.get("attempts")).isGreaterThanOrEqualTo(1);
            assertThat(d.get("last_status_code")).isEqualTo(503);
        });

        mvc.perform(shop.as(put("/webhooks/endpoints/{id}/receiver", endpoint)
                .contentType(MediaType.APPLICATION_JSON).content("{\"failing\":false}"))).andExpect(status().isOk());
        await().ignoreExceptions().atMost(WAIT).untilAsserted(() -> assertThat(onlyDelivery(endpoint).get("status")).isEqualTo("DELIVERED"));
        assertThat(received(endpoint).getFirst().get("attempt")).isNotEqualTo(1);
    }

    @Test
    void aMerchantServerThatKeepsFailingIsGivenUpOnAfterMaxAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> signatures = new CopyOnWriteArrayList<>();
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            calls.incrementAndGet();
            signatures.add(exchange.getRequestHeaders().getFirst(WebhookSigner.HEADER));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String created = mvc.perform(shop.as(post("/webhooks/endpoints").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":\"" + store + "\",\"url\":\"http://localhost:"
                                    + server.getAddress().getPort() + "/hook\"}")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String endpoint = JsonPath.read(created, "$.endpoint.id");
            String secret = JsonPath.read(created, "$.secret");
            sendTest(endpoint);

            await().ignoreExceptions().atMost(WAIT).untilAsserted(() -> assertThat(onlyDelivery(endpoint).get("status")).isEqualTo("FAILED"));
            assertThat(calls.get()).isEqualTo(3); // max-attempts in the test profile
            assertThat(onlyDelivery(endpoint).get("attempts")).isEqualTo(3);
            // The merchant can check every call really came from PayCore.
            long now = Instant.now().getEpochSecond();
            for (int i = 0; i < signatures.size(); i++) {
                assertThat(WebhookSigner.verify(secret, signatures.get(i), bodies.get(i), now)).isTrue();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void endpointsBelongToTheStoreOwnerAndTheSecretIsShownOnce() throws Exception {
        String endpoint = createTestReceiver();
        TestSessions.SignedIn stranger = TestSessions.guest(mvc);

        mvc.perform(stranger.as(get("/webhooks/endpoints/{id}/deliveries", endpoint))).andExpect(status().isNotFound());
        mvc.perform(stranger.as(delete("/webhooks/endpoints/{id}", endpoint))).andExpect(status().isNotFound());
        mvc.perform(stranger.as(post("/webhooks/endpoints").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + store + "\",\"testReceiver\":true}"))).andExpect(status().isNotFound());
        mvc.perform(shop.as(post("/webhooks/endpoints").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + wallet + "\",\"testReceiver\":true}"))).andExpect(status().isBadRequest());

        String list = mvc.perform(shop.as(get("/webhooks/endpoints"))).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(endpoint).doesNotContain("whsec_");

        mvc.perform(post("/webhooks/endpoints/{id}/test", endpoint)).andExpect(status().isForbidden()); // no session/CSRF
        mvc.perform(shop.as(delete("/webhooks/endpoints/{id}", endpoint))).andExpect(status().isNoContent());
    }

    @Test
    void theReceiverRejectsForgedCalls() throws Exception {
        String endpoint = createTestReceiver();
        mvc.perform(post("/webhooks/receiver/{id}", endpoint).contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.HEADER, WebhookSigner.header("whsec_guess", Instant.now().getEpochSecond(), "{}"))
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String pay(long amount, boolean capture) throws Exception {
        String body = mvc.perform(shop.as(post("/payments").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerAccountId\":\"" + wallet + "\",\"merchantAccountId\":\"" + store
                                + "\",\"amount\":" + amount + ",\"capture\":" + capture + "}")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createTestReceiver() throws Exception {
        ResultActions result = mvc.perform(shop.as(post("/webhooks/endpoints").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"" + store + "\",\"testReceiver\":true}")));
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.secret")).startsWith("whsec_");
        return JsonPath.read(body, "$.endpoint.id");
    }

    private void sendTest(String endpoint) throws Exception {
        mvc.perform(shop.as(post("/webhooks/endpoints/{id}/test", endpoint))).andExpect(status().isAccepted());
    }

    private List<Map<String, Object>> received(String endpoint) throws Exception {
        String body = mvc.perform(shop.as(get("/webhooks/endpoints/{id}/received", endpoint)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private Map<String, Object> onlyDelivery(String endpoint) {
        return jdbc.queryForMap("select status, attempts, last_status_code from webhook_deliveries where endpoint_id = ?",
                UUID.fromString(endpoint));
    }
}
