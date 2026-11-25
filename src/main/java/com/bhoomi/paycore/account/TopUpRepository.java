package com.bhoomi.paycore.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopUpRepository extends JpaRepository<TopUp, UUID> {

    Optional<TopUp> findByIdempotencyKey(String idempotencyKey);

    List<TopUp> findTop10ByStatusOrderByUpdatedAtDesc(TopUp.Status status);

    List<TopUp> findTop100ByStatusAndUpdatedAtBefore(TopUp.Status status, Instant cutoff);
}
