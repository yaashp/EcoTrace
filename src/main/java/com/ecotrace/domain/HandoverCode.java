package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A one-time collection code issued by a customer. Only a BCrypt hash is stored, never the code itself.
 * A code is usable until it is used, revoked, expires, or reaches the attempt limit.
 */
@Entity
@Table(name = "handover_code")
public class HandoverCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false)
    private Pickup pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant usedAt;

    private Instant revokedAt;

    protected HandoverCode() {
    }

    public HandoverCode(Pickup pickup, AppUser customer, String codeHash, Instant expiresAt, Instant createdAt) {
        this.pickup = pickup;
        this.customer = customer;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /** True while the code can still be entered: not used, not revoked, not expired, attempts left. */
    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now)
                && failedAttempts < HandoverRules.MAX_ATTEMPTS;
    }

    public int attemptsLeft() {
        return Math.max(0, HandoverRules.MAX_ATTEMPTS - failedAttempts);
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    public Long getId() { return id; }
    public Pickup getPickup() { return pickup; }
    public AppUser getCustomer() { return customer; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
