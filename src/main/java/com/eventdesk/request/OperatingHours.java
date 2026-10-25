package com.eventdesk.request;

import com.eventdesk.common.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Campus hours for events: 07:00 to 22:00 on a single day, in the college's
 * time zone. Also catches the common 24-hour-clock slip of typing 03:00 when
 * meaning 3 PM.
 *
 * <p>Extended technical events with department approval (24-hour hackathons)
 * may run outside these hours; see {@link EventDetails#allowsExtendedHours()}.
 *
 * <p>Kept outside EventRequest because it needs the time zone, while the
 * entity's rules work on plain instants.
 */
public final class OperatingHours {

    public static final LocalTime OPENS = LocalTime.of(7, 0);
    public static final LocalTime CLOSES = LocalTime.of(22, 0);

    private OperatingHours() {
    }

    public static void check(EventDetails details, ZoneId zone) {
        if (details.startsAt() == null || details.endsAt() == null) {
            return; // EventRequest.submit reports missing times
        }
        if (details.allowsExtendedHours() || within(details.startsAt(), details.endsAt(), zone)) {
            return;
        }
        throw new InvalidRequestException("Events must take place between " + OPENS + " and " + CLOSES
                + " on one day. If you meant an afternoon time, use the 24-hour clock (for example 15:00 for 3 PM). "
                + "Overnight technical events such as hackathons need the department's approval reference.");
    }

    /** Whether an event fits within campus hours on a single day. */
    public static boolean within(Instant startsAt, Instant endsAt, ZoneId zone) {
        ZonedDateTime start = startsAt.atZone(zone);
        ZonedDateTime end = endsAt.atZone(zone);
        LocalDate day = start.toLocalDate();
        boolean sameDay = end.toLocalDate().equals(day)
                || (end.toLocalDate().equals(day.plusDays(1)) && end.toLocalTime().equals(LocalTime.MIDNIGHT));
        return !start.toLocalTime().isBefore(OPENS) && sameDay && !end.toLocalTime().isAfter(CLOSES);
    }
}