package com.eventdesk.request;

import com.eventdesk.common.IllegalTransitionException;
import com.eventdesk.user.Role;

import java.util.Optional;

/**
 * Lifecycle of an event request:
 *
 * <pre>
 *   PENDING_VENUE --venue office approves--> PENDING_DEAN --dean approves--> APPROVED
 *        |                                        |
 *        +--venue office rejects--> REJECTED <--dean rejects--+
 *
 *   PENDING_VENUE / PENDING_DEAN / APPROVED --club cancels--> CANCELLED
 *   PENDING_VENUE / PENDING_DEAN --no decision by the start time--> EXPIRED (system)
 * </pre>
 */
public enum RequestStatus {
    PENDING_VENUE("Awaiting venue office"),
    PENDING_DEAN("Awaiting dean"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    /** No decision before the event started. Never approved; releases the slot. */
    EXPIRED("Expired");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Whether a request in this status keeps its venue slot reserved.
     * Pending requests hold the slot too, so two clubs can't both wait days
     * for the same hall. Must match the WHERE clause of the
     * no_overlapping_venue_bookings constraint in V1__create_schema.sql.
     */
    public boolean holdsVenue() {
        return this == PENDING_VENUE || this == PENDING_DEAN || this == APPROVED;
    }

    /** The role that must review a request in this status, or empty if no review is pending. */
    public Optional<Role> reviewer() {
        return switch (this) {
            case PENDING_VENUE -> Optional.of(Role.VENUE_OFFICE);
            case PENDING_DEAN -> Optional.of(Role.DEAN);
            case APPROVED, REJECTED, CANCELLED, EXPIRED -> Optional.empty();
        };
    }

    /** The status a request moves to when its current reviewer approves it. */
    public RequestStatus afterApproval() {
        return switch (this) {
            case PENDING_VENUE -> PENDING_DEAN;
            case PENDING_DEAN -> APPROVED;
            case APPROVED, REJECTED, CANCELLED, EXPIRED ->
                    throw new IllegalTransitionException("A request that is " + label.toLowerCase() + " cannot be approved");
        };
    }
}