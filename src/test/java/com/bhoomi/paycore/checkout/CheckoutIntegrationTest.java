package com.bhoomi.paycore.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.card.CardNumbers;
import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Payment links: a store asks for money, anyone pays with a PayCore card on the public checkout page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutIntegrationTest {

    @Autowired
    MockMvc mvc;

    TestSessions.SignedIn shop;
    TestSessions.SignedIn shopper;
    Card card;

    record Card(String number, int expMonth, int expYear, String cvc, String wallet) {
    }

    @BeforeEach
    void setUp() throws Exception {
        shop = TestSessions.guest(mvc);
        shopper = TestSessions.guest(mvc); // ₹5,000 in the wallet behind their card
        card = revealCard(shopper);
    }

    @Test
    void anyoneCanViewALinkAndPayItWithACardWithoutSigningIn() throws Exception {
        String link = createLink(49_900, "Hoodie");

        mvc.perform(get("/checkout-sessions/{id}", link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(49_900))
                .andExpect(jsonPath("$.description").value("Hoodie"));

        pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.cardLast4").value(card.number().substring(12)));

        mvc.perform(get("/checkout-sessions/{id}", link)).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(available(shopper, card.wallet())).isEqualTo(500_000 - 49_900);
    }

    @Test
    void aLinkCanOnlyBePaidOnce() throws Exception {
        String link = createLink(10_000, "Coffee");
        pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc()).andExpect(status().isOk());

        pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc()).andExpect(status().isConflict());
        assertThat(available(shopper, card.wallet())).isEqualTo(490_000);
    }

    @Test
    void twoCardsRacingToPayTheSameLinkCannotBothSucceed() throws Exception {
        String link = createLink(10_000, "Last ticket");
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            cards.add(revealCard(TestSessions.guest(mvc)));
        }

        List<Callable<Integer>> attempts = cards.stream().<Callable<Integer>>map(c -> () ->
                pay(link, c.number(), c.expMonth(), c.expYear(), c.cvc()).andReturn().getResponse().getStatus()).toList();
        int approved = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(cards.size())) {
            for (Future<Integer> f : pool.invokeAll(attempts)) {
                if (f.get() == 200) {
                    approved++;
                }
            }
        }
        assertThat(approved).isEqualTo(1);
    }

    @Test
    void declineCodesExplainWhatWentWrongAndMoveNoMoney() throws Exception {
        String link = createLink(10_000, "Socks");
        String badLuhn = card.number().substring(0, 15) + ((card.number().charAt(15) - '0' + 1) % 10);
        String wrongCvc = card.cvc().equals("000") ? "001" : "000";

        expectDecline(pay(link, badLuhn, card.expMonth(), card.expYear(), card.cvc()), "incorrect_number");
        expectDecline(pay(link, "4242424242424242", card.expMonth(), card.expYear(), card.cvc()), "card_not_supported");
        expectDecline(pay(link, CardNumbers.TEST_DECLINE, card.expMonth(), card.expYear(), "123"), "card_declined");
        expectDecline(pay(link, card.number(), 1, 2020, card.cvc()), "expired_card");
        expectDecline(pay(link, card.number(), card.expMonth(), card.expYear(), wrongCvc), "incorrect_cvc");

        assertThat(available(shopper, card.wallet())).isEqualTo(500_000);
        mvc.perform(get("/checkout-sessions/{id}", link)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void insufficientFundsIsADeclineAndTheLinkStaysPayable() throws Exception {
        String link = createLink(900_000, "Laptop"); // ₹9,000 > ₹5,000
        expectDecline(pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc()), "insufficient_funds");
        mvc.perform(get("/checkout-sessions/{id}", link)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void repeatedWrongCvcsLockTheCard() throws Exception {
        String link = createLink(10_000, "Gum");
        String wrongCvc = card.cvc().equals("000") ? "001" : "000";
        for (int i = 0; i < 5; i++) {
            expectDecline(pay(link, card.number(), card.expMonth(), card.expYear(), wrongCvc), "incorrect_cvc");
        }
        // Even the right CVC is refused now.
        expectDecline(pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc()), "card_declined");
    }

    @Test
    void onlyTheStoreOwnerCanCreateLinksForIt() throws Exception {
        String store = account(shop, "MERCHANT");
        mvc.perform(shopper.as(post("/checkout-sessions")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantAccountId\":\"" + store + "\",\"amount\":100,\"description\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/checkout-sessions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantAccountId\":\"" + store + "\",\"amount\":100,\"description\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thePublicViewRevealsNothingAboutThePayer() throws Exception {
        String link = createLink(10_000, "Tea");
        pay(link, card.number(), card.expMonth(), card.expYear(), card.cvc());
        String body = mvc.perform(get("/checkout-sessions/{id}", link)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(card.wallet()).doesNotContain(card.number()).doesNotContain("Guest's wallet");
    }

    // --- helpers ------------------------------------------------------------------------------

    private String createLink(long amount, String description) throws Exception {
        String store = account(shop, "MERCHANT");
        String body = mvc.perform(shop.as(post("/checkout-sessions")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantAccountId\":\"" + store + "\",\"amount\":" + amount + ",\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions pay(String link, String number, int expMonth, int expYear, String cvc) throws Exception {
        return mvc.perform(post("/checkout-sessions/{id}/pay", link).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"" + number + "\",\"expMonth\":" + expMonth + ",\"expYear\":" + expYear + ",\"cvc\":\"" + cvc + "\"}"));
    }

    private static void expectDecline(ResultActions result, String code) throws Exception {
        result.andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.declineCode").value(code));
    }

    private Card revealCard(TestSessions.SignedIn user) throws Exception {
        String cards = mvc.perform(user.as(get("/cards"))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(cards, "$[0].id");
        String revealed = mvc.perform(user.as(post("/cards/{id}/reveal", id))).andReturn().getResponse().getContentAsString();
        return new Card(JsonPath.read(revealed, "$.number"), JsonPath.read(revealed, "$.expMonth"),
                JsonPath.read(revealed, "$.expYear"), JsonPath.read(revealed, "$.cvc"), account(user, "CUSTOMER_WALLET"));
    }

    private String account(TestSessions.SignedIn user, String type) throws Exception {
        String body = mvc.perform(user.as(get("/accounts"))).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.type == '" + type + "')].id").getFirst();
    }

    private long available(TestSessions.SignedIn user, String account) throws Exception {
        String body = mvc.perform(user.as(get("/accounts/{id}", account))).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.balance.available")).longValue();
    }
}
