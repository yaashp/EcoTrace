package com.ecotrace.domain;

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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One step in the chain of custody: who changed what, and when. Append-only at the application level
 * (no setters, and the repository exposes inserts and reads). The actor is stored as an id plus a
 * snapshot of the name, so history survives later changes to the user record.
 */
@Entity
@Table(name = "pickup_event")
public class PickupEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id")
    private Pickup pickup;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PickupStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PickupStatus toStatus;

    private Long actorId;

    @Column(nullable = false, length = 80)
    private String actorName;

    /** A Role name, or SYSTEM. */
    @Column(nullable = false, length = 20)
    private String actorRole;

    /** Free text entered by the actor. Visible only to people allowed to see the full record. */
    @Column(length = 500)
    private String note;

    /** Text that is safe to show on the public tracking page. */
    @Column(length = 300)
    private String detail;

    @Column(nullable = false)
    private Instant occurredAt;

    protected PickupEvent() {
    }

    public PickupEvent(Pickup pickup, PickupStatus fromStatus, PickupStatus toStatus, Long actorId,
                       String actorName, String actorRole, String note, String detail, Instant occurredAt) {
        this.pickup = pickup;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.note = note;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Pickup getPickup() { return pickup; }
    public PickupStatus getFromStatus() { return fromStatus; }
    public PickupStatus getToStatus() { return toStatus; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getNote() { return note; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
