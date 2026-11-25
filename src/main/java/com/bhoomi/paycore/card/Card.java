package com.bhoomi.paycore.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A virtual card linked to a customer wallet. Holds only the encrypted number, never the CVC. */
@Entity
@Table(name = "cards")
public class Card {

    public enum Status { ACTIVE, FROZEN }

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String last4;

    @Column(name = "exp_month", nullable = false)
    private int expMonth;

    @Column(name = "exp_year", nullable = false)
    private int expYear;

    @Column(name = "pan_encrypted", nullable = false)
    private String panEncrypted;

    @Column(name = "pan_fingerprint", nullable = false, unique = true)
    private String panFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Card() {
        // for JPA
    }

    public Card(UUID id, UUID accountId, UUID userId, String last4, int expMonth, int expYear,
                String panEncrypted, String panFingerprint) {
        this.id = id;
        this.accountId = accountId;
        this.userId = userId;
        this.brand = "PAYCORE";
        this.last4 = last4;
        this.expMonth = expMonth;
        this.expYear = expYear;
        this.panEncrypted = panEncrypted;
        this.panFingerprint = panFingerprint;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBrand() {
        return brand;
    }

    public String getLast4() {
        return last4;
    }

    public int getExpMonth() {
        return expMonth;
    }

    public int getExpYear() {
        return expYear;
    }

    public String getPanEncrypted() {
        return panEncrypted;
    }

    public String getPanFingerprint() {
        return panFingerprint;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
