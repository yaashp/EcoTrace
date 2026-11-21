package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Records every attempted status change, including blocked ones. Contains no passwords or tokens. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(length = 80)
    private String actorName;

    @Column(length = 20)
    private String actorRole;

    @Column(length = 20)
    private String pickupReference;

    private boolean success;

    @Column(length = 600)
    private String message;

    private boolean demo;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditLog() {
    }

    public AuditLog(Long actorId, String actorName, String actorRole, String pickupReference,
                    boolean success, String message, boolean demo) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.pickupReference = pickupReference;
        this.success = success;
        this.message = message;
        this.demo = demo;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getPickupReference() { return pickupReference; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isDemo() { return demo; }
    public Instant getOccurredAt() { return occurredAt; }
}
