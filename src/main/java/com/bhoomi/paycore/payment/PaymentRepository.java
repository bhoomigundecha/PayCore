package com.bhoomi.paycore.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** The payment for a checkout session that isn't FAILED, if any (at most one, by a unique index). */
    Optional<Payment> findFirstByCheckoutSessionIdAndStatusNot(UUID checkoutSessionId, PaymentStatus status);

    /** Most recently changed payments, across everyone (for the anonymous activity feed). */
    List<Payment> findTop20ByOrderByUpdatedAtDesc();

    List<Payment> findTop100ByStatusInAndUpdatedAtBefore(Collection<PaymentStatus> statuses, Instant cutoff);

    @Query("select p from Payment p where p.customerAccountId in :accounts or p.merchantAccountId in :accounts order by p.createdAt desc")
    List<Payment> findRecentForAccounts(@Param("accounts") Collection<UUID> accounts, Pageable page);
}
