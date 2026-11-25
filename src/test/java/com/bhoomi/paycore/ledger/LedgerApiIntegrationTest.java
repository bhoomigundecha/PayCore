package com.bhoomi.paycore.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
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
class LedgerApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void totalDebitsAlwaysEqualTotalCreditsAndIsPublic() throws Exception {
        TestSessions.guest(mvc); // makes sure there is fresh activity on the ledger

        mvc.perform(get("/ledger/totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void transferHistoryShowsBothLegsOfAPayment() throws Exception {
        var user = TestSessions.guest(mvc);
        String customer = createAccount(user, "CUSTOMER_WALLET");
        String merchant = createAccount(user, "MERCHANT");
        mvc.perform(user.as(post("/accounts/{id}/top-ups", customer)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5000}")).andExpect(status().isOk());
        mvc.perform(user.as(post("/payments")).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerAccountId\":\"" + customer + "\",\"merchantAccountId\":\"" + merchant + "\",\"amount\":1000}"))
                .andExpect(status().isCreated());

        // top-up + 2 holds (merchant share, fee) + 2 captures
        mvc.perform(user.as(get("/ledger/accounts/{id}/transfers", customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].kind").value("POST_PENDING"))
                .andExpect(jsonPath("$[4].code").value("TOP_UP"));
    }

    @Test
    void someoneElsesAccountHistoryIs404() throws Exception {
        var owner = TestSessions.guest(mvc);
        var stranger = TestSessions.guest(mvc);
        String account = createAccount(owner, "CUSTOMER_WALLET");

        mvc.perform(stranger.as(get("/ledger/accounts/{id}/transfers", account))).andExpect(status().isNotFound());
    }

    @Test
    void systemAccountHistoryIsVisibleToAnySignedInUser() throws Exception {
        var user = TestSessions.guest(mvc);
        mvc.perform(user.as(get("/ledger/accounts/{id}/transfers", SystemAccounts.PLATFORM_FEE_INR)))
                .andExpect(status().isOk());
    }

    @Test
    void transferHistoryNeedsSignIn() throws Exception {
        mvc.perform(get("/ledger/accounts/{id}/transfers", SystemAccounts.PLATFORM_FEE_INR))
                .andExpect(status().isUnauthorized());
    }

    private String createAccount(TestSessions.SignedIn user, String type) throws Exception {
        String body = mvc.perform(user.as(post("/accounts")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\",\"name\":\"Ledger test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
