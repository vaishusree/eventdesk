package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.InvalidRequestException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import com.eventdesk.venue.Venue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** An "Other" event must say what kind of event it is; other types never carry that text. */
class OtherEventTypeTest {

    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    private static final Instant START = Instant.parse("2026-12-05T04:30:00Z");   // 10:00 IST, past the 30-day notice
    private static final Instant END = START.plus(Duration.ofHours(3));

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Venue hall = new Venue("Seminar Hall A", "Academic Block 1", 300);
    private final AppUser coding = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);

    private static EventDetails event(EventType type, String detail) {
        return new EventDetails("Meet", "desc", type, 50, START, END, null, detail);
    }

    @Test
    void otherEventStoresWhatKindOfEventItIs() {
        EventRequest request = EventRequest.submit(coding, hall, event(EventType.OTHER, "  Alumni meet  "), NOW);

        assertEquals("Alumni meet", request.getEventTypeDetail(), "stored trimmed");
        assertEquals("Other: Alumni meet", request.eventTypeLabel());
    }

    @Test
    void otherEventWithoutAKindIsRefused() {
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(coding, hall, event(EventType.OTHER, null), NOW));
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(coding, hall, event(EventType.OTHER, "   "), NOW));
    }

    @Test
    void kindLongerThanSixtyCharactersIsRefused() {
        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(coding, hall, event(EventType.OTHER, "x".repeat(61)), NOW));
    }

    @Test
    void otherTypesIgnoreTheKindField() {
        // e.g. the club typed something, then switched the type to Workshop
        EventRequest workshop = EventRequest.submit(coding, hall, event(EventType.WORKSHOP, "Alumni meet"), NOW);

        assertNull(workshop.getEventTypeDetail());
        assertEquals("Workshop", workshop.eventTypeLabel());
    }
}