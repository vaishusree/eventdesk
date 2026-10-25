package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.InvalidRequestException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import com.eventdesk.venue.Venue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 24-hour hackathons and other extended technical events. */
class ExtendedEventTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    // A Saturday 10:00 IST, well past the 30-day notice
    private static final Instant SAT_10AM = LocalDate.of(2026, 12, 5).atTime(LocalTime.of(10, 0)).atZone(IST).toInstant();
    private static final String APPROVAL = "CSE/HOD/2026/114";

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Venue lab = new Venue("Seminar Hall A", "Academic Block 1", 150);
    private final AppUser coding = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);

    private static EventDetails event(EventType type, Duration length, String approvalRef) {
        return new EventDetails("Hackathon", "24-hour build", type, 120, SAT_10AM, SAT_10AM.plus(length), approvalRef);
    }

    @Test
    void technicalEventWithDepartmentApprovalMayRunTwentyFourHours() {
        EventRequest hackathon = EventRequest.submit(coding, lab, event(EventType.TECHNICAL, Duration.ofHours(24), " " + APPROVAL + " "), NOW);

        assertEquals(RequestStatus.PENDING_VENUE, hackathon.getStatus());
        assertEquals(APPROVAL, hackathon.getDepartmentApprovalRef(), "reference is stored, trimmed");
    }

    @Test
    void technicalEventLongerThanTwelveHoursNeedsTheApprovalReference() {
        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(coding, lab, event(EventType.TECHNICAL, Duration.ofHours(24), null), NOW));
        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(coding, lab, event(EventType.TECHNICAL, Duration.ofHours(24), "   "), NOW));
    }

    @Test
    void evenExtendedEventsHaveALimit() {
        EventRequest exactly36 = EventRequest.submit(coding, lab,
                event(EventType.TECHNICAL, EventRequest.MAX_EXTENDED_DURATION, APPROVAL), NOW);
        assertEquals(RequestStatus.PENDING_VENUE, exactly36.getStatus());

        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(coding, lab,
                event(EventType.TECHNICAL, EventRequest.MAX_EXTENDED_DURATION.plusMinutes(1), APPROVAL), NOW));
    }

    @Test
    void onlyTechnicalEventsCanBeExtended() {
        for (EventType other : new EventType[] {EventType.WORKSHOP, EventType.CULTURAL, EventType.WEBINAR, EventType.OTHER}) {
            assertThrows(InvalidRequestException.class,
                    () -> EventRequest.submit(coding, lab, event(other, Duration.ofHours(13), APPROVAL), NOW),
                    other + " must not run longer than 12 hours");
        }
    }

    @Test
    void approvalReferenceOnAStandardNonTechnicalEventIsNotStored() {
        EventRequest workshop = EventRequest.submit(coding, lab, event(EventType.WORKSHOP, Duration.ofHours(3), APPROVAL), NOW);

        assertNull(workshop.getDepartmentApprovalRef());
    }

    @Test
    void approvalReferenceHasAMaximumLength() {
        String tooLong = "x".repeat(101);
        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(coding, lab, event(EventType.TECHNICAL, Duration.ofHours(24), tooLong), NOW));
    }

    @Test
    void overnightIsAllowedOutsideCampusHoursOnlyWithApproval() {
        Instant tenPm = LocalDate.of(2026, 12, 5).atTime(LocalTime.of(22, 0)).atZone(IST).toInstant();
        Instant sixAm = tenPm.plus(Duration.ofHours(8));
        EventDetails withApproval = new EventDetails("Night hack", "d", EventType.TECHNICAL, 50, tenPm, sixAm, APPROVAL);
        EventDetails without = new EventDetails("Night hack", "d", EventType.TECHNICAL, 50, tenPm, sixAm);

        OperatingHours.check(withApproval, IST);
        assertThrows(InvalidRequestException.class, () -> OperatingHours.check(without, IST));
    }
}