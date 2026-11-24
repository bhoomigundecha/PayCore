package com.bhoomi.paycore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.ledger.PaymentLedger;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end through HTTP, Postgres and the real local TigerBeetle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentLedger paymentLedger;

    @Autowired
    RecoveryJob recoveryJob;

    String customer;
    String merchant;

    @BeforeEach
    void setUp() throws Exception {
        customer = createAccount("CUSTOMER_WALLET", "Asha");
        merchant = createAccount("MERCHANT", "Chai Point");
        topUp(customer, 100_000, UUID.randomUUID().toString()).andExpect(status().isOk());
    }

    @Test
    void paymentWithAutoCaptureMovesMoneyAndTakesTheFee() throws Exception {
        createPayment(UUID.randomUUID().toString(), 10_000, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.fee").value(200));

        assertThat(available(customer)).isEqualTo(90_000);
        assertThat(available(merchant)).isEqualTo(9_800);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSamePaymentAndChargesOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = JsonPath.read(createPayment(key, 10_000, true).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(createPayment(key, 10_000, true).andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(available(customer)).isEqualTo(90_000);
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        createPayment(key, 10_000, true).andExpect(status().isCreated());

        createPayment(key, 5_000, true).andExpect(status().isUnprocessableContent());
    }

    @Test
    void authorizeCaptureRefund() throws Exception {
        String id = JsonPath.read(createPayment(UUID.randomUUID().toString(), 10_000, false)
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(available(customer)).isEqualTo(90_000); // held, not spendable

        mvc.perform(post("/payments/{id}/capture", id)).andExpect(jsonPath("$.status").value("CAPTURED"));
        mvc.perform(post("/payments/{id}/refund", id)).andExpect(jsonPath("$.status").value("REFUNDED"));

        assertThat(available(customer)).isEqualTo(100_000);
        assertThat(available(merchant)).isZero();
    }

    @Test
    void cancelledPaymentCannotBeCaptured() throws Exception {
        String id = JsonPath.read(createPayment(UUID.randomUUID().toString(), 10_000, false)
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/payments/{id}/cancel", id)).andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(post("/payments/{id}/capture", id)).andExpect(status().isConflict());
        assertThat(available(customer)).isEqualTo(100_000);
    }

    @Test
    void insufficientFundsIsRecordedAsFailed() throws Exception {
        createPayment(UUID.randomUUID().toString(), 500_000, true)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("insufficient_funds"));
        assertThat(available(customer)).isEqualTo(100_000);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(10_000, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topUpRetryCreditsOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        topUp(customer, 5_000, key).andExpect(status().isOk());
        topUp(customer, 5_000, key).andExpect(status().isOk());

        assertThat(available(customer)).isEqualTo(105_000);
    }

    /**
     * Simulates a crash after the ledger moved the money but before Postgres recorded it:
     * the payment row is stuck in AUTHORIZING while TigerBeetle already holds the funds.
     * The recovery job must finish it without charging the customer twice.
     */
    @Test
    void recoveryFinishesAPaymentInterruptedAfterTheLedgerWrite() throws Exception {
        UUID customerId = UUID.fromString(customer);
        UUID merchantId = UUID.fromString(merchant);
        Payment stuck = paymentRepository.saveAndFlush(new Payment(UUID.randomUUID(), UUID.randomUUID().toString(),
                customerId, merchantId, 10_000, 200, Currency.INR, true));
        paymentLedger.authorize(stuck.getId(), customerId, merchantId, 10_000, 200, Currency.INR);

        recoveryJob.run();

        assertThat(paymentRepository.findById(stuck.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        assertThat(available(customer)).isEqualTo(90_000);
        assertThat(available(merchant)).isEqualTo(9_800);
    }

    private String createAccount(String type, String name) throws Exception {
        String body = mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions topUp(String accountId, long amount, String key) throws Exception {
        return mvc.perform(post("/accounts/{id}/top-ups", accountId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private ResultActions createPayment(String key, long amount, boolean capture) throws Exception {
        return mvc.perform(post("/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentBody(amount, capture)));
    }

    private String paymentBody(long amount, boolean capture) {
        return "{\"customerAccountId\":\"" + customer + "\",\"merchantAccountId\":\"" + merchant
                + "\",\"amount\":" + amount + ",\"capture\":" + capture + "}";
    }

    private long available(String accountId) throws Exception {
        String body = mvc.perform(get("/accounts/{id}", accountId)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.balance.available")).longValue();
    }
}
