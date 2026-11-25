package com.bhoomi.paycore.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Large checkout payments need the cardholder's one-time code, like 3-D Secure. */
@SpringBootTest(properties = "paycore.step-up.threshold=200000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutStepUpIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void largeCheckoutWaitsForTheCardholdersCodeThenPays() throws Exception {
        var shop = TestSessions.guest(mvc);
        var shopper = TestSessions.guest(mvc);
        String store = first(shop, "MERCHANT");
        String link = JsonPath.read(mvc.perform(shop.as(post("/checkout-sessions")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantAccountId\":\"" + store + "\",\"amount\":300000,\"description\":\"Headphones\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        String cardId = JsonPath.read(mvc.perform(shopper.as(get("/cards"))).andReturn().getResponse().getContentAsString(), "$[0].id");
        String c = mvc.perform(shopper.as(post("/cards/{id}/reveal", cardId))).andReturn().getResponse().getContentAsString();

        String body = mvc.perform(post("/checkout-sessions/{id}/pay", link).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + JsonPath.read(c, "$.number") + "\",\"expMonth\":" + JsonPath.read(c, "$.expMonth")
                                + ",\"expYear\":" + JsonPath.read(c, "$.expYear") + ",\"cvc\":\"" + JsonPath.read(c, "$.cvc") + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("REQUIRES_ACTION"))
                .andReturn().getResponse().getContentAsString();
        String payment = JsonPath.read(body, "$.paymentId");
        mvc.perform(get("/checkout-sessions/{id}", link)).andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));

        // The code went to the cardholder's phone, not to whoever typed the card.
        String inbox = mvc.perform(shopper.as(get("/notifications"))).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(JsonPath.<String>read(inbox, "$[0].text"));
        assertThat(m.find()).isTrue();

        mvc.perform(post("/checkout-sessions/{id}/authenticate", link).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + payment + "\",\"code\":\"" + m.group(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"));
        mvc.perform(get("/checkout-sessions/{id}", link)).andExpect(jsonPath("$.status").value("PAID"));
    }

    private String first(TestSessions.SignedIn user, String type) throws Exception {
        String body = mvc.perform(user.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.type == '" + type + "')].id").getFirst();
    }
}
