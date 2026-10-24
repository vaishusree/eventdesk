package com.eventdesk.request;

import java.time.Instant;

/**
 * What a club fills in when it asks for a venue. Validated by {@link EventRequest#submit}.
 * {@code expectedAttendance} is null when not given; it is required only for
 * event types where {@link EventType#requiresAttendance()} is true.
 */
public record EventDetails(
        String title,
        String description,
        EventType eventType,
        Integer expectedAttendance,
        Instant startsAt,
        Instant endsAt
) {
}