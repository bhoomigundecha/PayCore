package com.bhoomi.paycore.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LedgerService ledger;

    @Autowired
    PaymentLedger paymentLedger;

    @Test
    void totalDebitsAlwaysEqualTotalCredits() throws Exception {
        UUID customer = ledger.createAccount(AccountType.CUSTOMER_WALLET, Currency.INR);
        UUID merchant = ledger.createAccount(AccountType.MERCHANT, Currency.INR);
        ledger.submit(Currency.INR, LedgerTransfer.posted(LedgerService.newId(),
                SystemAccounts.EXTERNAL_FUNDING_INR, customer, 5_000, TransferCode.TOP_UP));
        paymentLedger.authorize(UUID.randomUUID(), customer, merchant, 1_000, 20, Currency.INR);

        mvc.perform(get("/ledger/totals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void transferHistoryShowsBothLegsOfAPayment() throws Exception {
        UUID customer = ledger.createAccount(AccountType.CUSTOMER_WALLET, Currency.INR);
        UUID merchant = ledger.createAccount(AccountType.MERCHANT, Currency.INR);
        ledger.submit(Currency.INR, LedgerTransfer.posted(LedgerService.newId(),
                SystemAccounts.EXTERNAL_FUNDING_INR, customer, 5_000, TransferCode.TOP_UP));
        UUID payment = UUID.randomUUID();
        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, Currency.INR);
        paymentLedger.capture(payment, 20, Currency.INR);

        // top-up + 2 holds + 2 captures
        mvc.perform(get("/ledger/accounts/{id}/transfers", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].kind").value("POST_PENDING"))
                .andExpect(jsonPath("$[4].code").value("TOP_UP"));
    }

    @Test
    void unknownAccountHistoryIs404() throws Exception {
        mvc.perform(get("/ledger/accounts/{id}/transfers", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
