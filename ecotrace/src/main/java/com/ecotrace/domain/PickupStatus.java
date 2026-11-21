package com.ecotrace.domain;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a pickup. The allowed transitions are defined only in allowedNext(). */
public enum PickupStatus {
    REQUESTED("Pickup requested"),
    CONFIRMED("Pickup confirmed"),
    COLLECTED("E-waste collected"),
    AT_FACILITY("Received at collection facility"),
    TRANSFERRED("Transferred to recycling partner"),
    RECEIVED_BY_PARTNER("Received by recycling partner"),
    PROCESSING("Processing in progress"),
    RECYCLING_CONFIRMED("Processing outcome confirmed"),
    COMPLETED("Case completed"),
    FAILED_PICKUP("Pickup failed"),
    REJECTED("Rejected by recycling partner"),
    CANCELLED("Cancelled");

    private final String label;

    PickupStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isException() {
        return this == FAILED_PICKUP || this == REJECTED || this == CANCELLED;
    }

    /** Closed pickups no longer need any action. */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** True once the e-waste has physically left the customer. */
    public boolean isCollectedOrLater() {
        return switch (this) {
            case COLLECTED, AT_FACILITY, TRANSFERRED, RECEIVED_BY_PARTNER, PROCESSING,
                    RECYCLING_CONFIRMED, COMPLETED, REJECTED -> true;
            default -> false;
        };
    }

    public Set<PickupStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(COLLECTED, FAILED_PICKUP, CANCELLED);
            case FAILED_PICKUP -> EnumSet.of(CONFIRMED, CANCELLED);
            case COLLECTED -> EnumSet.of(AT_FACILITY);
            case AT_FACILITY -> EnumSet.of(TRANSFERRED);
            case TRANSFERRED -> EnumSet.of(RECEIVED_BY_PARTNER);
            case RECEIVED_BY_PARTNER -> EnumSet.of(PROCESSING, REJECTED);
            case PROCESSING -> EnumSet.of(RECYCLING_CONFIRMED, REJECTED);
            case RECYCLING_CONFIRMED -> EnumSet.of(COMPLETED);
            case REJECTED -> EnumSet.of(TRANSFERRED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(PickupStatus.class);
        };
    }

    public boolean canMoveTo(PickupStatus target) {
        return allowedNext().contains(target);
    }
}
