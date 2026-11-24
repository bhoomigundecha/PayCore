package com.bhoomi.paycore.payment;

import com.bhoomi.paycore.ledger.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    @Column(name = "merchant_account_id", nullable = false)
    private UUID merchantAccountId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(name = "auto_capture", nullable = false)
    private boolean autoCapture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Optimistic locking: concurrent updates to the same payment cannot silently overwrite each other. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    public Payment(UUID id, String idempotencyKey, UUID customerAccountId, UUID merchantAccountId,
                   long amount, long fee, Currency currency, boolean autoCapture) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerAccountId = customerAccountId;
        this.merchantAccountId = merchantAccountId;
        this.amount = amount;
        this.fee = fee;
        this.currency = currency;
        this.autoCapture = autoCapture;
        this.status = PaymentStatus.AUTHORIZING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void moveTo(PaymentStatus newStatus) {
        this.status = newStatus;
    }

    void moveTo(PaymentStatus newStatus, String reason) {
        this.status = newStatus;
        this.failureReason = reason;
    }

    boolean sameRequestAs(UUID customer, UUID merchant, long amount, boolean autoCapture) {
        return customerAccountId.equals(customer) && merchantAccountId.equals(merchant)
                && this.amount == amount && this.autoCapture == autoCapture;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCustomerAccountId() {
        return customerAccountId;
    }

    public UUID getMerchantAccountId() {
        return merchantAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public long getFee() {
        return fee;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isAutoCapture() {
        return autoCapture;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
