package com.bhoomi.paycore.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
class AuthAndOwnershipIntegrationTest {

    @Autowired
    MockMvc mvc;

    // --- authentication -----------------------------------------------------------------------

    @Test
    void signUpSignsYouInAndGivesAStarterKit() throws Exception {
        var user = TestSessions.signUp(mvc, TestSessions.uniqueEmail(), "correct horse battery");

        mvc.perform(user.as(get("/auth/me")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.guest").value(false));

        // A wallet with ₹5,000 and a virtual card, plus a store to receive payments.
        String accounts = mvc.perform(user.as(get("/accounts"))).andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(accounts, "$[*].type")).containsExactly("CUSTOMER_WALLET", "MERCHANT");
        assertThat(JsonPath.<Integer>read(accounts, "$[0].balance.available")).isEqualTo(500_000);
        mvc.perform(user.as(get("/cards"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void loginWithTheRightPasswordWorksAndTheWrongOneIs401() throws Exception {
        String email = TestSessions.uniqueEmail();
        TestSessions.signUp(mvc, email, "correct horse battery");

        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong password!\"}"))
                .andExpect(status().isUnauthorized());
        // Unknown email gets the identical answer, so accounts can't be discovered by probing.
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = TestSessions.uniqueEmail();
        TestSessions.signUp(mvc, email, "correct horse battery");

        mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Copy\",\"email\":\"" + email + "\",\"password\":\"another password\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"" + TestSessions.uniqueEmail() + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postsWithoutACsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/auth/guest")).andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        var user = TestSessions.guest(mvc);
        mvc.perform(user.as(post("/auth/logout"))).andExpect(status().isNoContent());
        mvc.perform(get("/accounts").cookie(user.sessionCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void apiNeedsSignIn() throws Exception {
        mvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/payments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/cards")).andExpect(status().isUnauthorized());
    }

    // --- ownership ----------------------------------------------------------------------------

    @Test
    void youCannotSeeOrSpendSomeoneElsesWallet() throws Exception {
        var victim = TestSessions.guest(mvc);
        var attacker = TestSessions.guest(mvc);
        String victimWallet = firstAccount(victim, "CUSTOMER_WALLET");
        String attackerStore = firstAccount(attacker, "MERCHANT");

        mvc.perform(attacker.as(get("/accounts/{id}", victimWallet))).andExpect(status().isNotFound());
        mvc.perform(attacker.as(post("/accounts/{id}/top-ups", victimWallet)).header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}")).andExpect(status().isNotFound());
        mvc.perform(attacker.as(post("/payments")).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerAccountId\":\"" + victimWallet + "\",\"merchantAccountId\":\"" + attackerStore + "\",\"amount\":100000}"))
                .andExpect(status().isNotFound());

        mvc.perform(victim.as(get("/accounts/{id}", victimWallet)))
                .andExpect(jsonPath("$.balance.available").value(500_000)); // untouched
    }

    @Test
    void onlyTheMerchantCanCaptureAndOutsidersCannotSeeThePayment() throws Exception {
        var shopper = TestSessions.guest(mvc);
        var shop = TestSessions.guest(mvc);
        var outsider = TestSessions.guest(mvc);
        String wallet = firstAccount(shopper, "CUSTOMER_WALLET");
        String store = firstAccount(shop, "MERCHANT");

        String body = mvc.perform(shopper.as(post("/payments")).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerAccountId\":\"" + wallet + "\",\"merchantAccountId\":\"" + store + "\",\"amount\":1000,\"capture\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantName").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String payment = JsonPath.read(body, "$.id");

        mvc.perform(outsider.as(get("/payments/{id}", payment))).andExpect(status().isNotFound());
        mvc.perform(shopper.as(post("/payments/{id}/capture", payment))).andExpect(status().isForbidden());
        mvc.perform(shop.as(post("/payments/{id}/capture", payment))).andExpect(jsonPath("$.status").value("CAPTURED"));

        // Both parties see it in their payment list.
        mvc.perform(shopper.as(get("/payments"))).andExpect(jsonPath("$[0].id").value(payment));
        mvc.perform(shop.as(get("/payments"))).andExpect(jsonPath("$[0].id").value(payment));
    }

    @Test
    void idempotencyKeysAreScopedPerUser() throws Exception {
        var a = TestSessions.guest(mvc);
        var b = TestSessions.guest(mvc);
        String key = "same-key-" + UUID.randomUUID();
        String storeA = firstAccount(a, "MERCHANT");
        String storeB = firstAccount(b, "MERCHANT");

        String payA = paymentId(mvc.perform(a.as(post("/payments")).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerAccountId\":\"" + firstAccount(a, "CUSTOMER_WALLET") + "\",\"merchantAccountId\":\"" + storeA + "\",\"amount\":100}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String payB = paymentId(mvc.perform(b.as(post("/payments")).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerAccountId\":\"" + firstAccount(b, "CUSTOMER_WALLET") + "\",\"merchantAccountId\":\"" + storeB + "\",\"amount\":100}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(payA).isNotEqualTo(payB);
    }

    private String firstAccount(TestSessions.SignedIn user, String type) throws Exception {
        String body = mvc.perform(user.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.type == '" + type + "')].id").getFirst();
    }

    private static String paymentId(String body) {
        return JsonPath.read(body, "$.id");
    }
}
