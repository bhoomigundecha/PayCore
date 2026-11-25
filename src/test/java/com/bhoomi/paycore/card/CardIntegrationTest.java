package com.bhoomi.paycore.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhoomi.paycore.support.TestSessions;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cardsAreListedMaskedAndRevealedOnlyToTheOwner() throws Exception {
        var owner = TestSessions.guest(mvc);
        var stranger = TestSessions.guest(mvc);

        String list = mvc.perform(owner.as(get("/cards")))
                .andExpect(jsonPath("$[0].last4").isString())
                .andExpect(jsonPath("$[0].number").doesNotExist())
                .andExpect(jsonPath("$[0].cvc").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String cardId = JsonPath.read(list, "$[0].id");
        String last4 = JsonPath.read(list, "$[0].last4");

        String revealed = mvc.perform(owner.as(post("/cards/{id}/reveal", cardId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(revealed, "$.number");
        String cvc = JsonPath.read(revealed, "$.cvc");

        assertThat(number).startsWith(CardNumbers.BIN).hasSize(16).endsWith(last4);
        assertThat(CardNumbers.isValid(number)).isTrue();
        assertThat(cvc).matches("\\d{3}");

        // Revealing again gives the same derived CVC.
        String again = mvc.perform(owner.as(post("/cards/{id}/reveal", cardId))).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(again, "$.cvc")).isEqualTo(cvc);

        mvc.perform(stranger.as(post("/cards/{id}/reveal", cardId))).andExpect(status().isNotFound());
    }

    @Test
    void theDatabaseNeverHoldsTheCardNumberOrCvcInPlainText() throws Exception {
        var owner = TestSessions.guest(mvc);
        String cardId = JsonPath.read(mvc.perform(owner.as(get("/cards"))).andReturn().getResponse().getContentAsString(), "$[0].id");
        String revealed = mvc.perform(owner.as(post("/cards/{id}/reveal", cardId))).andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(revealed, "$.number");

        List<Map<String, Object>> rows = jdbc.queryForList("select * from cards where id = ?::uuid", cardId);
        assertThat(rows).hasSize(1);
        String everyColumn = rows.getFirst().values().toString();
        assertThat(everyColumn).doesNotContain(number);
        assertThat(rows.getFirst()).doesNotContainKeys("cvc", "cvv", "pan", "number");
    }
}
