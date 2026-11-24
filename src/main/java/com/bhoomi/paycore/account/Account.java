package com.bhoomi.paycore.account;

import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer wallet or merchant. Its id is also its TigerBeetle account id.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(UUID id, AccountType type, String name, Currency currency) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
