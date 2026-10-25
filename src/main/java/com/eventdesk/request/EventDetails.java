package com.eventdesk.request;

import java.time.Instant;

/**
 * What a club fills in when it asks for a venue. Validated by {@link EventRequest#submit}.
 * {@code expectedAttendance} is null when not given; it is required only for
 * event types where {@link EventType#requiresAttendance()} is true.
 * {@code departmentApprovalRef} is only needed for extended technical events
 * (longer than 12 hours or outside campus hours, e.g. 24-hour hackathons).
 */
public record EventDetails(
        String title,
        String description,
        EventType eventType,
        Integer expectedAttendance,
        Instant startsAt,
        Instant endsAt,
        String departmentApprovalRef
) {

    /** For standard events, which need no department approval reference. */
    public EventDetails(String title, String description, EventType eventType, Integer expectedAttendance,
                        Instant startsAt, Instant endsAt) {
        this(title, description, eventType, expectedAttendance, startsAt, endsAt, null);
    }

    /**
     * Extended events (24-hour hackathons and the like) are allowed only for
     * technical events, and only with the department's approval reference.
     */
    public boolean allowsExtendedHours() {
        return eventType == EventType.TECHNICAL
                && departmentApprovalRef != null && !departmentApprovalRef.isBlank();
    }
}