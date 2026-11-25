package com.bhoomi.paycore.checkout;

import com.bhoomi.paycore.ledger.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A payment link: "pay this merchant this amount". Paid at most once (enforced by a unique index on payments). */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession {

    static final Duration LIFETIME = Duration.ofHours(24);

    @Id
    private UUID id;

    @Column(name = "merchant_account_id", nullable = false)
    private UUID merchantAccountId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CheckoutSession() {
        // for JPA
    }

    public CheckoutSession(UUID id, UUID merchantAccountId, UUID createdByUserId, long amount, Currency currency,
                           String description) {
        this.id = id;
        this.merchantAccountId = merchantAccountId;
        this.createdByUserId = createdByUserId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(LIFETIME);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantAccountId() {
        return merchantAccountId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public long getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
