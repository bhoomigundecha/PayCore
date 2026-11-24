package com.bhoomi.paycore.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findTop100ByStatusInAndUpdatedAtBefore(Collection<PaymentStatus> statuses, Instant cutoff);
}
