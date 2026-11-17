package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A verified physical handover: created when the collector enters the customer's code correctly, and
 * completed when the collector records the pickup as collected. One handover per pickup.
 */
@Entity
@Table(name = "handover")
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String handoverReference;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false, unique = true)
    private Pickup pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collector_id", nullable = false)
    private AppUser collector;

    @Column(nullable = false)
    private Instant verifiedAt;

    private Instant collectedAt;

    protected Handover() {
    }

    public Handover(String handoverReference, Pickup pickup, AppUser customer, AppUser collector, Instant verifiedAt) {
        this.handoverReference = handoverReference;
        this.pickup = pickup;
        this.customer = customer;
        this.collector = collector;
        this.verifiedAt = verifiedAt;
    }

    public void markCollected(Instant at) {
        this.collectedAt = at;
    }

    public Long getId() { return id; }
    public String getHandoverReference() { return handoverReference; }
    public Pickup getPickup() { return pickup; }
    public AppUser getCustomer() { return customer; }
    public AppUser getCollector() { return collector; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCollectedAt() { return collectedAt; }
}
