package com.bhoomi.paycore.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void feedIsPublicShowsNewPaymentsAndRevealsNothingPersonal() throws Exception {
        var user = TestSessions.guest(mvc);
        String accounts = mvc.perform(user.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        String wallet = JsonPath.<List<String>>read(accounts, "$[?(@.type == 'CUSTOMER_WALLET')].id").getFirst();
        String store = JsonPath.<List<String>>read(accounts, "$[?(@.type == 'MERCHANT')].id").getFirst();
        long amount = 12_345; // distinctive, so we can find our own event
        String payment = mvc.perform(user.as(post("/payments")).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerAccountId\":\"" + wallet + "\",\"merchantAccountId\":\"" + store + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        // No session: the feed is public.
        String body = mvc.perform(get("/activity")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> events = JsonPath.read(body, "$");

        assertThat(events).anySatisfy(e -> {
            assertThat(e.get("kind")).isEqualTo("CAPTURED");
            assertThat(((Number) e.get("amount")).longValue()).isEqualTo(amount);
        });
        assertThat(events).allSatisfy(e -> assertThat(e.keySet()).containsExactlyInAnyOrder("key", "kind", "amount", "fee", "currency", "at"));
        // Nothing that identifies the payer, the merchant or the payment.
        assertThat(body).doesNotContain(wallet).doesNotContain(store).doesNotContain((String) JsonPath.read(payment, "$.id"))
                .doesNotContain("Guest");
    }
}
