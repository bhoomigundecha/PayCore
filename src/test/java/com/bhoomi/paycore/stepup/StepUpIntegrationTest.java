package com.bhoomi.paycore.stepup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.payment.PaymentService;
import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Payments of ₹2,000 or more need the payer's one-time code before the ledger is touched. */
@SpringBootTest(properties = "paycore.step-up.threshold=200000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StepUpIntegrationTest {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    MockMvc mvc;

    @Autowired
    PaymentService paymentService;

    TestSessions.SignedIn payer;
    String wallet;
    String store;

    @BeforeEach
    void setUp() throws Exception {
        payer = TestSessions.guest(mvc); // wallet starts with ₹5,000
        wallet = account(payer, "CUSTOMER_WALLET");
        store = account(payer, "MERCHANT");
    }

    @Test
    void smallPaymentsGoStraightThrough() throws Exception {
        pay(10_000).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void largePaymentWaitsForTheCodeAndTheLedgerIsUntouched() throws Exception {
        pay(300_000).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("REQUIRES_AUTHENTICATION"));

        assertThat(available()).isEqualTo(500_000);
        // Only the welcome top-up exists in the ledger for this wallet: no hold was placed.
        mvc.perform(payer.as(get("/ledger/accounts/{id}/transfers", wallet))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theRightCodeApprovesAndMovesTheMoney() throws Exception {
        String payment = id(pay(300_000));

        authenticate(payment, codeFor("₹3,000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        assertThat(available()).isEqualTo(200_000);
    }

    @Test
    void threeWrongCodesFailThePaymentForGood() throws Exception {
        String payment = id(pay(300_000));
        String code = codeFor("₹3,000.00");
        String wrong = code.equals("000000") ? "111111" : "000000";

        authenticate(payment, wrong).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.attemptsLeft").value(2));
        authenticate(payment, wrong).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.attemptsLeft").value(1));
        authenticate(payment, wrong)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("authentication_failed"));

        // Even the right code is useless now.
        authenticate(payment, code).andExpect(status().isConflict());
        assertThat(available()).isEqualTo(500_000);
    }

    @Test
    void aCodeOnlyApprovesThePaymentItWasIssuedFor() throws Exception {
        String first = id(pay(300_000));
        String second = id(pay(250_000));

        // The ₹3,000 code must not approve the ₹2,500 payment (dynamic linking).
        authenticate(second, codeFor("₹3,000.00")).andExpect(status().isUnprocessableContent());
        authenticate(first, codeFor("₹3,000.00")).andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void onlyThePayerCanApprove() throws Exception {
        var stranger = TestSessions.guest(mvc);
        String payment = id(pay(300_000));

        mvc.perform(stranger.as(post("/payments/{id}/authenticate", payment)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}")).andExpect(status().isNotFound());
    }

    @Test
    void resendingRightAwayIsRefused() throws Exception {
        String payment = id(pay(300_000));
        mvc.perform(payer.as(post("/payments/{id}/resend-code", payment))).andExpect(status().isConflict());
    }

    @Test
    void unapprovedPaymentsExpire() throws Exception {
        String payment = id(pay(300_000));

        paymentService.expireUnapproved(Instant.now().plusSeconds(1));

        mvc.perform(payer.as(get("/payments/{id}", payment)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("authentication_expired"));
    }

    @Test
    void theInboxIsPrivate() throws Exception {
        pay(300_000);
        var stranger = TestSessions.guest(mvc);
        mvc.perform(stranger.as(get("/notifications"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    // --- helpers ------------------------------------------------------------------------------

    private ResultActions pay(long amount) throws Exception {
        return mvc.perform(payer.as(post("/payments")).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerAccountId\":\"" + wallet + "\",\"merchantAccountId\":\"" + store + "\",\"amount\":" + amount + "}"));
    }

    private ResultActions authenticate(String paymentId, String code) throws Exception {
        return mvc.perform(payer.as(post("/payments/{id}/authenticate", paymentId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    /** Reads the code from the payer's simulated phone, picking the message for the given amount. */
    private String codeFor(String amountText) throws Exception {
        String inbox = mvc.perform(payer.as(get("/notifications"))).andReturn().getResponse().getContentAsString();
        List<String> texts = JsonPath.read(inbox, "$[*].text");
        String message = texts.stream().filter(t -> t.contains(amountText)).findFirst().orElseThrow();
        Matcher m = CODE.matcher(message);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long available() throws Exception {
        String body = mvc.perform(payer.as(get("/accounts/{id}", wallet))).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.balance.available")).longValue();
    }

    private String account(TestSessions.SignedIn user, String type) throws Exception {
        String body = mvc.perform(user.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.type == '" + type + "')].id").getFirst();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
