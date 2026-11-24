package com.bhoomi.paycore.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against the real local TigerBeetle. Every test uses fresh accounts, so tests are independent.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentLedgerIntegrationTest {

    private static final Currency INR = Currency.INR;
    private static final long STARTING_BALANCE = 10_000;

    @Autowired
    LedgerService ledger;

    @Autowired
    PaymentLedger paymentLedger;

    UUID customer;
    UUID merchant;

    @BeforeEach
    void setUp() {
        customer = ledger.createAccount(AccountType.CUSTOMER_WALLET, INR);
        merchant = ledger.createAccount(AccountType.MERCHANT, INR);
        ledger.submit(INR, LedgerTransfer.posted(LedgerService.newId(),
                SystemAccounts.externalFunding(INR), customer, STARTING_BALANCE, TransferCode.TOP_UP));
    }

    @Test
    void authorizeHoldsMoneyAndCaptureMovesIt() {
        long feesBefore = feeAccountCredits();
        UUID payment = UUID.randomUUID();

        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);

        assertThat(ledger.getBalance(customer).available()).isEqualTo(9_000);
        assertThat(ledger.getBalance(customer).debitsPending()).isEqualTo(1_000);
        assertThat(ledger.getBalance(merchant).creditsPending()).isEqualTo(980);
        assertThat(ledger.getBalance(merchant).creditsPosted()).isZero();

        paymentLedger.capture(payment, 20, INR);

        assertThat(ledger.getBalance(customer).debitsPending()).isZero();
        assertThat(ledger.getBalance(customer).debitsPosted()).isEqualTo(1_000);
        assertThat(ledger.getBalance(merchant).creditsPosted()).isEqualTo(980);
        assertThat(feeAccountCredits() - feesBefore).isEqualTo(20);
    }

    @Test
    void cancelReleasesTheHold() {
        UUID payment = UUID.randomUUID();
        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);

        paymentLedger.cancel(payment, 20, INR);

        assertThat(ledger.getBalance(customer).available()).isEqualTo(STARTING_BALANCE);
        assertThat(ledger.getBalance(merchant).creditsPending()).isZero();
        assertThat(ledger.getBalance(merchant).creditsPosted()).isZero();
    }

    @Test
    void retryingEveryStepNeverMovesMoneyTwice() {
        UUID payment = UUID.randomUUID();

        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);
        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);
        paymentLedger.capture(payment, 20, INR);
        paymentLedger.capture(payment, 20, INR);

        assertThat(ledger.getBalance(customer).debitsPosted()).isEqualTo(1_000);
        assertThat(ledger.getBalance(merchant).creditsPosted()).isEqualTo(980);
    }

    @Test
    void authorizeIsRejectedWhenFundsAreInsufficientAndNothingIsHeld() {
        UUID payment = UUID.randomUUID();

        assertThatThrownBy(() -> paymentLedger.authorize(payment, customer, merchant, 20_000, 400, INR))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(ledger.getBalance(customer).debitsPending()).isZero();
        assertThat(ledger.getBalance(merchant).creditsPending()).isZero();
    }

    @Test
    void captureAfterCancelIsRejected() {
        UUID payment = UUID.randomUUID();
        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);
        paymentLedger.cancel(payment, 20, INR);

        assertThatThrownBy(() -> paymentLedger.capture(payment, 20, INR))
                .isInstanceOf(LedgerConflictException.class);
        assertThat(ledger.getBalance(merchant).creditsPosted()).isZero();
    }

    @Test
    void refundReturnsTheFullAmountToTheCustomer() {
        UUID payment = UUID.randomUUID();
        paymentLedger.authorize(payment, customer, merchant, 1_000, 20, INR);
        paymentLedger.capture(payment, 20, INR);

        paymentLedger.refund(payment, customer, merchant, 1_000, 20, INR);

        assertThat(ledger.getBalance(customer).available()).isEqualTo(STARTING_BALANCE);
        assertThat(ledger.getBalance(merchant).available()).isZero();
    }

    @Test
    void concurrentPaymentsNeverOverdrawTheWallet() throws Exception {
        int attempts = 50;
        long amount = 1_000; // the wallet can afford exactly 10 of these
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                try {
                    paymentLedger.authorize(UUID.randomUUID(), customer, merchant, amount, 20, INR);
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            });
        }

        int succeeded = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                if (result.get()) {
                    succeeded++;
                }
            }
        }

        assertThat(succeeded).isEqualTo(10);
        assertThat(ledger.getBalance(customer).available()).isZero();
    }

    private long feeAccountCredits() {
        return ledger.getBalance(SystemAccounts.platformFee(INR)).creditsPosted();
    }
}
