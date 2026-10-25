package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.IllegalTransitionException;
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

/**
 * The "nobody answered" policy. Scenario: the hall is confirmed for 10:00 IST,
 * the dean never responds, and the clock runs up to and past the start time.
 */
class PermissionPolicyTest {

    // 5 Nov 2026, 10:00 IST
    private static final Instant EVENT_START = Instant.parse("2026-11-05T04:30:00Z");
    private static final Instant SUBMITTED_AT = EVENT_START.minus(Duration.ofDays(35));
    private static final Instant VENUE_CONFIRMED_AT = EVENT_START.minus(Duration.ofDays(8));
    private static final Instant QUARTER_TO_TEN = EVENT_START.minus(Duration.ofMinutes(15));

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Venue hall = new Venue("Seminar Hall A", "Academic Block 1", 150);
    private final AppUser coding = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);
    private final AppUser venueOffice = new AppUser("venue@example.org", "hash", "Venue Office", Role.VENUE_OFFICE, null);
    private final AppUser dean = new AppUser("dean@example.org", "hash", "Dean", Role.DEAN, null);

    /** Hall confirmed by the venue office 8 days before; the dean hasn't acted. */
    private EventRequest hallConfirmedDeanSilent() {
        EventDetails details = new EventDetails("Hackathon", "Day-long build", EventType.TECHNICAL, 100,
                EVENT_START, EVENT_START.plus(Duration.ofHours(6)));
        EventRequest request = EventRequest.submit(coding, hall, details, SUBMITTED_AT);
        request.approve(venueOffice, VENUE_CONFIRMED_AT);
        return request;
    }

    @Test
    void pendingIsNeverPermissionEvenWhenTheHallIsConfirmed() {
        EventRequest request = hallConfirmedDeanSilent();

        assertEquals(HallStatus.CONFIRMED, request.hallStatus(QUARTER_TO_TEN));
        assertEquals(PermissionStatus.PENDING, request.permissionStatus(QUARTER_TO_TEN));
        assertFalse(request.isAuthorized());
    }

    @Test
    void requestEscalatesSevenDaysBeforeTheEvent() {
        EventRequest request = hallConfirmedDeanSilent();
        Instant justBeforeWindow = EVENT_START.minus(EventRequest.ESCALATION_WINDOW).minusSeconds(1);
        Instant windowOpens = EVENT_START.minus(EventRequest.ESCALATION_WINDOW);

        assertFalse(request.isEscalated(justBeforeWindow));
        assertTrue(request.isEscalated(windowOpens));
        assertTrue(request.isEscalated(QUARTER_TO_TEN));
    }

    @Test
    void clubCanRemindOnceEscalatedEvenIfTheStageIsNotYetOverdue() {
        EventRequest request = hallConfirmedDeanSilent();
        Instant windowOpens = EVENT_START.minus(EventRequest.ESCALATION_WINDOW);

        // The dean stage is only 1 day old, so the 2-day overdue rule alone would block this.
        assertFalse(request.isOverdue(windowOpens));
        assertTrue(request.canRemind(coding, windowOpens));
        request.remind(coding, windowOpens);
        assertEquals(ActionType.REMINDED, request.getHistory().get(request.getHistory().size() - 1).getAction());
    }

    @Test
    void deanCanStillDecideUpToTheStartTime() {
        EventRequest request = hallConfirmedDeanSilent();

        request.approve(dean, QUARTER_TO_TEN);

        assertTrue(request.isAuthorized());
        assertEquals(PermissionStatus.GRANTED, request.permissionStatus(EVENT_START));
        assertFalse(request.isExpired(EVENT_START), "an approved request never expires");
    }

    @Test
    void noDecisionByTheStartTimeMeansExpiredAndNotApproved() {
        EventRequest request = hallConfirmedDeanSilent();

        assertTrue(request.isExpired(EVENT_START));
        assertEquals(PermissionStatus.EXPIRED, request.permissionStatus(EVENT_START));
        assertFalse(request.isAuthorized());
        assertEquals(HallStatus.RELEASED, request.hallStatus(EVENT_START));
    }

    @Test
    void expiredRequestLeavesTheQueueAndAcceptsNoActions() {
        EventRequest request = hallConfirmedDeanSilent();
        Instant afterStart = EVENT_START.plus(Duration.ofMinutes(5));

        assertFalse(request.isLivePending(afterStart), "must not appear in the review queue");
        assertFalse(request.isEscalated(afterStart));
        assertFalse(request.isOverdue(afterStart));
        assertFalse(request.canRemind(coding, afterStart));

        assertThrows(IllegalTransitionException.class, () -> request.approve(dean, afterStart));
        assertThrows(IllegalTransitionException.class, () -> request.reject(dean, "too late", afterStart));
        assertThrows(IllegalTransitionException.class, () -> request.remind(coding, afterStart));
        assertThrows(IllegalTransitionException.class, () -> request.cancel(coding, afterStart));
        assertFalse(request.isAuthorized());
    }

    @Test
    void permissionAndHallStatusForFinishedRequests() {
        EventRequest rejected = hallConfirmedDeanSilent();
        rejected.reject(dean, "Clashes with exams", QUARTER_TO_TEN);
        assertEquals(PermissionStatus.DENIED, rejected.permissionStatus(EVENT_START));
        assertEquals(HallStatus.RELEASED, rejected.hallStatus(EVENT_START));

        EventRequest withdrawn = hallConfirmedDeanSilent();
        withdrawn.cancel(coding, QUARTER_TO_TEN);
        assertEquals(PermissionStatus.WITHDRAWN, withdrawn.permissionStatus(EVENT_START));
        assertEquals(HallStatus.RELEASED, withdrawn.hallStatus(EVENT_START));

        EventDetails details = new EventDetails("Talk", "Guest talk", EventType.OTHER, null,
                EVENT_START, EVENT_START.plus(Duration.ofHours(1)), null, "Guest lecture");
        EventRequest tentative = EventRequest.submit(coding, hall, details, SUBMITTED_AT);
        assertEquals(HallStatus.TENTATIVE, tentative.hallStatus(QUARTER_TO_TEN));


    }
}