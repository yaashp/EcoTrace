package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private boolean active = true;
    private boolean demo;

    /** The organisation a RECYCLER account works for. Null for other roles. */
    @ManyToOne
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String name, String email, String phone, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public Partner getPartner() { return partner; }
    public void setPartner(Partner v) { this.partner = v; }
    public Instant getCreatedAt() { return createdAt; }
}
