package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** In-app notification. Email and SMS delivery are not implemented. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(length = 20)
    private String pickupReference;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {
    }

    public Notification(AppUser user, String type, String title, String message, String pickupReference, Instant createdAt) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.pickupReference = pickupReference;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getPickupReference() { return pickupReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant v) { this.readAt = v; }

    /** Used by the demo seed to mark older notifications as already read. */
    public Notification markReadIf(boolean condition, Instant at) {
        if (condition) {
            this.readAt = at;
        }
        return this;
    }
}
