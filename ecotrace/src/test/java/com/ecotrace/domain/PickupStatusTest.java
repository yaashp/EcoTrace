package com.ecotrace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PickupStatusTest {

    @Test
    void happyPathIsAllowedStepByStep() {
        assertTrue(PickupStatus.REQUESTED.canMoveTo(PickupStatus.CONFIRMED));
        assertTrue(PickupStatus.CONFIRMED.canMoveTo(PickupStatus.COLLECTED));
        assertTrue(PickupStatus.COLLECTED.canMoveTo(PickupStatus.AT_FACILITY));
        assertTrue(PickupStatus.AT_FACILITY.canMoveTo(PickupStatus.TRANSFERRED));
        assertTrue(PickupStatus.TRANSFERRED.canMoveTo(PickupStatus.RECEIVED_BY_PARTNER));
        assertTrue(PickupStatus.RECEIVED_BY_PARTNER.canMoveTo(PickupStatus.PROCESSING));
        assertTrue(PickupStatus.PROCESSING.canMoveTo(PickupStatus.RECYCLING_CONFIRMED));
        assertTrue(PickupStatus.RECYCLING_CONFIRMED.canMoveTo(PickupStatus.COMPLETED));
    }

    @Test
    void collectionDoesNotImplyRecycling() {
        assertFalse(PickupStatus.COLLECTED.canMoveTo(PickupStatus.RECYCLING_CONFIRMED));
        assertFalse(PickupStatus.AT_FACILITY.canMoveTo(PickupStatus.COMPLETED));
        assertFalse(PickupStatus.TRANSFERRED.canMoveTo(PickupStatus.RECYCLING_CONFIRMED));
    }

    @Test
    void skippingStepsAndGoingBackwardsIsRejected() {
        assertFalse(PickupStatus.REQUESTED.canMoveTo(PickupStatus.COLLECTED));
        assertFalse(PickupStatus.COLLECTED.canMoveTo(PickupStatus.CONFIRMED));
        assertFalse(PickupStatus.COMPLETED.canMoveTo(PickupStatus.PROCESSING));
    }

    @Test
    void closedStatesHaveNoNextStep() {
        assertTrue(PickupStatus.COMPLETED.allowedNext().isEmpty());
        assertTrue(PickupStatus.CANCELLED.allowedNext().isEmpty());
        assertTrue(PickupStatus.COMPLETED.isClosed());
    }

    @Test
    void failedAndRejectedPickupsCanBeRecovered() {
        assertTrue(PickupStatus.FAILED_PICKUP.canMoveTo(PickupStatus.CONFIRMED));
        assertTrue(PickupStatus.REJECTED.canMoveTo(PickupStatus.TRANSFERRED));
        assertEquals(2, PickupStatus.FAILED_PICKUP.allowedNext().size());
    }

    @Test
    void onlyRecyclersRecordRecyclingFacts() {
        for (PickupStatus s : new PickupStatus[]{PickupStatus.RECEIVED_BY_PARTNER, PickupStatus.PROCESSING, PickupStatus.RECYCLING_CONFIRMED}) {
            assertTrue(TransitionPolicy.mayRecord(Role.RECYCLER, s));
            assertFalse(TransitionPolicy.mayRecord(Role.ADMIN, s));
            assertFalse(TransitionPolicy.mayRecord(Role.COLLECTOR, s));
            assertFalse(TransitionPolicy.mayRecord(Role.CUSTOMER, s));
        }
    }

    @Test
    void customersCanOnlyCancel() {
        for (PickupStatus s : PickupStatus.values()) {
            assertEquals(s == PickupStatus.CANCELLED, TransitionPolicy.mayRecord(Role.CUSTOMER, s));
        }
    }

    @Test
    void onlyAdminsConfirmAndClose() {
        assertTrue(TransitionPolicy.mayRecord(Role.ADMIN, PickupStatus.CONFIRMED));
        assertTrue(TransitionPolicy.mayRecord(Role.ADMIN, PickupStatus.COMPLETED));
        assertFalse(TransitionPolicy.mayRecord(Role.COLLECTOR, PickupStatus.CONFIRMED));
        assertFalse(TransitionPolicy.mayRecord(Role.RECYCLER, PickupStatus.COMPLETED));
    }
}
