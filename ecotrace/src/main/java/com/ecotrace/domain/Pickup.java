package com.ecotrace.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A booking and, at the same time, the consignment that is tracked through custody. */
@Entity
@Table(name = "pickup")
public class Pickup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public tracking reference shown in the QR code. Unique and not derived from any personal data. */
    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collector_id")
    private AppUser collector;

    /** Collection facility where the e-waste was received. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Partner facility;

    /** Recycling partner the consignment was transferred to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PickupStatus status = PickupStatus.REQUESTED;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String locality;

    @Column(nullable = false, length = 15)
    private String contactPhone;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(nullable = false, length = 20)
    private String timeSlot;

    @Column(length = 500)
    private String instructions;

    private Double estimatedWeightKg;
    private Double verifiedWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Outcome outcome;

    @Column(length = 100)
    private String processingReference;

    @Column(length = 500)
    private String reason;

    private boolean demo;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @OneToMany(mappedBy = "pickup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PickupItem> items = new ArrayList<>();

    protected Pickup() {
    }

    public Pickup(String reference, AppUser customer) {
        this.reference = reference;
        this.customer = customer;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(PickupItem item) {
        item.setPickup(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public AppUser getCustomer() { return customer; }
    public AppUser getCollector() { return collector; }
    public void setCollector(AppUser v) { this.collector = v; }
    public Partner getFacility() { return facility; }
    public void setFacility(Partner v) { this.facility = v; }
    public Partner getPartner() { return partner; }
    public void setPartner(Partner v) { this.partner = v; }
    public PickupStatus getStatus() { return status; }
    public void setStatus(PickupStatus v) { this.status = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getLocality() { return locality; }
    public void setLocality(String v) { this.locality = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String v) { this.contactPhone = v; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate v) { this.pickupDate = v; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String v) { this.timeSlot = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { this.instructions = v; }
    public Double getEstimatedWeightKg() { return estimatedWeightKg; }
    public void setEstimatedWeightKg(Double v) { this.estimatedWeightKg = v; }
    public Double getVerifiedWeightKg() { return verifiedWeightKg; }
    public void setVerifiedWeightKg(Double v) { this.verifiedWeightKg = v; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome v) { this.outcome = v; }
    public String getProcessingReference() { return processingReference; }
    public void setProcessingReference(String v) { this.processingReference = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public List<PickupItem> getItems() { return items; }
}
