package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A collection facility or recycling partner. Seed entries are samples and never marked verified. */
@Entity
@Table(name = "partner")
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerType type;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String locality;

    @Column(length = 300)
    private String serviceArea;

    /** Comma-separated values from Catalog.CATEGORIES. */
    @Column(nullable = false, length = 500)
    private String acceptedCategories;

    @Column(length = 120)
    private String contactEmail;

    @Column(length = 20)
    private String contactPhone;

    /** Comma-separated: DOORSTEP, DROP_OFF. */
    @Column(length = 60)
    private String services;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verification verification = Verification.SAMPLE;

    @Column(length = 300)
    private String verificationNote;

    private Double lat;
    private Double lng;
    private boolean active = true;
    private boolean demo;

    protected Partner() {
    }

    public Partner(String name, PartnerType type) {
        this.name = name;
        this.type = type;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public PartnerType getType() { return type; }
    public void setType(PartnerType v) { this.type = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getLocality() { return locality; }
    public void setLocality(String v) { this.locality = v; }
    public String getServiceArea() { return serviceArea; }
    public void setServiceArea(String v) { this.serviceArea = v; }
    public String getAcceptedCategories() { return acceptedCategories; }
    public void setAcceptedCategories(String v) { this.acceptedCategories = v; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String v) { this.contactEmail = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String v) { this.contactPhone = v; }
    public String getServices() { return services; }
    public void setServices(String v) { this.services = v; }
    public Verification getVerification() { return verification; }
    public void setVerification(Verification v) { this.verification = v; }
    public String getVerificationNote() { return verificationNote; }
    public void setVerificationNote(String v) { this.verificationNote = v; }
    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }
    public Double getLng() { return lng; }
    public void setLng(Double v) { this.lng = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
}
