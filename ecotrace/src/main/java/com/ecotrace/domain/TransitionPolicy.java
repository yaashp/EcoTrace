package com.ecotrace.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which role may record which target status. Checked on the server for every update.
 * Administrators can confirm, cancel, close and re-route, but they cannot record facts that only a
 * recycling partner can know (receipt, processing, the final outcome).
 */
public final class TransitionPolicy {

    private static final Set<PickupStatus> CUSTOMER = EnumSet.of(PickupStatus.CANCELLED);

    private static final Set<PickupStatus> COLLECTOR = EnumSet.of(
            PickupStatus.COLLECTED, PickupStatus.AT_FACILITY, PickupStatus.TRANSFERRED, PickupStatus.FAILED_PICKUP);

    private static final Set<PickupStatus> RECYCLER = EnumSet.of(
            PickupStatus.RECEIVED_BY_PARTNER, PickupStatus.PROCESSING,
            PickupStatus.RECYCLING_CONFIRMED, PickupStatus.REJECTED);

    private static final Set<PickupStatus> ADMIN = EnumSet.of(
            PickupStatus.CONFIRMED, PickupStatus.CANCELLED, PickupStatus.COMPLETED,
            PickupStatus.AT_FACILITY, PickupStatus.TRANSFERRED);

    private TransitionPolicy() {
    }

    public static boolean mayRecord(Role role, PickupStatus target) {
        return switch (role) {
            case CUSTOMER -> CUSTOMER.contains(target);
            case COLLECTOR -> COLLECTOR.contains(target);
            case RECYCLER -> RECYCLER.contains(target);
            case ADMIN -> ADMIN.contains(target);
        };
    }
}
