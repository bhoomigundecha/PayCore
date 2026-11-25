package com.bhoomi.paycore.checkout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    List<CheckoutSession> findTop20ByCreatedByUserIdOrderByCreatedAtDesc(UUID userId);
}
