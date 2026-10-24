package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.IllegalTransitionException;
import com.eventdesk.common.NotPermittedException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import com.eventdesk.venue.Venue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverdueAndReminderTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-20T04:30:00Z");
    // 40 days after submission, so the 30-day notice rule is met.
    private static final Instant EVENT_START = SUBMITTED_AT.plus(Duration.ofDays(40));

    private static final Duration JUST_UNDER_TWO_DAYS = EventRequest.OVERDUE_AFTER.minusMinutes(1);
    private static final Duration JUST_OVER_TWO_DAYS = EventRequest.OVERDUE_AFTER.plusMinutes(1);

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Club roboticsClub = new Club("Robotics Club", "robotics@example.org");
    private final Venue hall = new Venue("Seminar Hall A", "Academic Block 1", 150);

    private final AppUser coding = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);
    private final AppUser robotics = new AppUser("robotics@example.org", "hash", "Robotics", Role.CLUB, roboticsClub);
    private final AppUser venueOffice = new AppUser("venue@example.org", "hash", "Venue Office", Role.VENUE_OFFICE, null);
    private final AppUser dean = new AppUser("dean@example.org", "hash", "Dean", Role.DEAN, null);

    private EventRequest submitted() {
        EventDetails details = new EventDetails("Hackathon", "24h build", EventType.TECHNICAL, 100,
                EVENT_START, EVENT_START.plus(Duration.ofHours(8)));
        return EventRequest.submit(coding, hall, details, SUBMITTED_AT);
    }

    private static Instant after(Duration d) {
        return SUBMITTED_AT.plus(d);
    }

    // ---- Overdue ------------------------------------------------------------

    @Test
    void pendingRequestBecomesOverdueAfterTwoDays() {
        EventRequest request = submitted();

        assertFalse(request.isOverdue(after(JUST_UNDER_TWO_DAYS)));
        assertFalse(request.isOverdue(after(EventRequest.OVERDUE_AFTER)), "exactly two days is not yet overdue");
        assertTrue(request.isOverdue(after(JUST_OVER_TWO_DAYS)));
    }

    @Test
    void overdueClockRestartsWhenTheRequestMovesToTheDean() {
        EventRequest request = submitted();
        Instant venueApprovedAt = after(Duration.ofDays(3));
        assertTrue(request.isOverdue(venueApprovedAt));

        request.approve(venueOffice, venueApprovedAt);

        assertFalse(request.isOverdue(venueApprovedAt.plus(Duration.ofDays(1))),
                "the dean should not inherit the venue office's delay");
        assertTrue(request.isOverdue(venueApprovedAt.plus(JUST_OVER_TWO_DAYS)));
        assertEquals(Duration.ofDays(1), request.timeInCurrentStatus(venueApprovedAt.plus(Duration.ofDays(1))));
    }

    @Test
    void finishedRequestsAreNeverOverdue() {
        EventRequest rejected = submitted();
        rejected.reject(venueOffice, "Hall closed", after(Duration.ofHours(1)));
        assertFalse(rejected.isOverdue(after(Duration.ofDays(5))));

        EventRequest approved = submitted();
        approved.approve(venueOffice, after(Duration.ofHours(1)));
        approved.approve(dean, after(Duration.ofHours(2)));
        assertFalse(approved.isOverdue(after(Duration.ofDays(5))));
    }

    // ---- Reminders ----------------------------------------------------------

    @Test
    void clubCanRemindOnceOverdueAndItIsRecordedWithoutChangingStatus() {
        EventRequest request = submitted();
        Instant now = after(JUST_OVER_TWO_DAYS);

        assertTrue(request.canRemind(coding, now));
        request.remind(coding, now);

        assertEquals(RequestStatus.PENDING_VENUE, request.getStatus());
        assertEquals(now, request.getLastReminderAt());
        RequestAction reminder = request.getHistory().get(request.getHistory().size() - 1);
        assertEquals(ActionType.REMINDED, reminder.getAction());
        assertEquals(RequestStatus.PENDING_VENUE, reminder.getFromStatus());
        assertEquals(RequestStatus.PENDING_VENUE, reminder.getToStatus());
        assertTrue(request.isOverdue(now), "a reminder must not reset the overdue clock");
    }

    @Test
    void cannotRemindBeforeTheRequestIsOverdue() {
        EventRequest request = submitted();

        assertFalse(request.canRemind(coding, after(JUST_UNDER_TWO_DAYS)));
        assertThrows(IllegalTransitionException.class, () -> request.remind(coding, after(JUST_UNDER_TWO_DAYS)));
    }

    @Test
    void atMostOneReminderPerDay() {
        EventRequest request = submitted();
        Instant first = after(JUST_OVER_TWO_DAYS);
        request.remind(coding, first);

        Instant tooSoon = first.plus(EventRequest.REMINDER_COOLDOWN).minusSeconds(1);
        assertFalse(request.canRemind(coding, tooSoon));
        assertThrows(IllegalTransitionException.class, () -> request.remind(coding, tooSoon));

        Instant nextDay = first.plus(EventRequest.REMINDER_COOLDOWN);
        assertTrue(request.canRemind(coding, nextDay));
        request.remind(coding, nextDay);
        assertEquals(nextDay, request.getLastReminderAt());
    }

    @Test
    void onlyTheOwningClubCanRemind() {
        EventRequest request = submitted();
        Instant now = after(JUST_OVER_TWO_DAYS);

        assertFalse(request.canRemind(robotics, now));
        assertThrows(NotPermittedException.class, () -> request.remind(robotics, now));
        assertThrows(NotPermittedException.class, () -> request.remind(venueOffice, now));
        assertThrows(NotPermittedException.class, () -> request.remind(dean, now));
    }

    @Test
    void cannotRemindAboutAFinishedRequestOrAPastEvent() {
        EventRequest rejected = submitted();
        rejected.reject(venueOffice, "Hall closed", after(Duration.ofHours(1)));
        assertFalse(rejected.canRemind(coding, after(Duration.ofDays(3))));
        assertThrows(IllegalTransitionException.class, () -> rejected.remind(coding, after(Duration.ofDays(3))));

        EventRequest stale = submitted();
        Instant afterEventStarted = EVENT_START.plusSeconds(1);
        assertFalse(stale.canRemind(coding, afterEventStarted));
        assertThrows(IllegalTransitionException.class, () -> stale.remind(coding, afterEventStarted));
    }
}
