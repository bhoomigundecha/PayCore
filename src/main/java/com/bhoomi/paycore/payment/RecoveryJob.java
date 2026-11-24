package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.account.TopUp;
import com.bhoomi.paycore.account.TopUpRepository;
import com.bhoomi.paycore.ledger.LedgerException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes work that was started but not completed: payments stuck in an "-ING" status and
 * PENDING top-ups (e.g. the app crashed or the ledger was down mid-request). Safe because every
 * ledger write uses a pre-decided transfer id, so re-running it can never move money twice.
 */
@Component
public class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final TopUpRepository topUps;
    private final AccountService accountService;
    private final Duration stuckAfter;

    public RecoveryJob(PaymentRepository payments, PaymentService paymentService,
                       TopUpRepository topUps, AccountService accountService,
                       @Value("${paycore.recovery.stuck-after:30s}") Duration stuckAfter) {
        this.payments = payments;
        this.paymentService = paymentService;
        this.topUps = topUps;
        this.accountService = accountService;
        this.stuckAfter = stuckAfter;
    }

    @Scheduled(fixedDelayString = "${paycore.recovery.interval:10s}")
    public void run() {
        Instant cutoff = Instant.now().minus(stuckAfter);
        try {
            for (Payment payment : payments.findTop100ByStatusInAndUpdatedAtBefore(PaymentStatus.IN_FLIGHT, cutoff)) {
                PaymentStatus before = payment.getStatus();
                Payment result = paymentService.advance(payment);
                log.info("Recovered payment {}: {} -> {}", payment.getId(), before, result.getStatus());
            }
            for (TopUp topUp : topUps.findTop100ByStatusAndUpdatedAtBefore(TopUp.Status.PENDING, cutoff)) {
                accountService.completeTopUp(topUp);
                log.info("Recovered top-up {}", topUp.getId());
            }
        } catch (LedgerException e) {
            log.warn("Recovery paused, ledger unavailable: {}", e.getMessage());
        }
    }
}
