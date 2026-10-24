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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who decides when the regular reviewer doesn't, and what happens when nobody does. */
class EscalationAndExpiryTest {

    private static final Instant EVENT_START = Instant.parse("2026-11-05T04:30:00Z");
    private static final Instant SUBMITTED_AT = EVENT_START.minus(Duration.ofDays(35));
    private static final Instant BEFORE_ESCALATION = EVENT_START.minus(Duration.ofDays(10));
    private static final Instant ESCALATED = EVENT_START.minus(Duration.ofDays(3));

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Venue hall = new Venue("Seminar Hall A", "Academic Block 1", 150);
    private final AppUser coding = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);
    private final AppUser venueOffice = new AppUser("venue@example.org", "hash", "Venue Office", Role.VENUE_OFFICE, null);
    private final AppUser dean = new AppUser("dean@example.org", "hash", "Dean", Role.DEAN, null);
    private final AppUser backup = new AppUser("backup@example.org", "hash", "Backup", Role.BACKUP_APPROVER, null);

    private EventRequest submitted() {
        EventDetails details = new EventDetails("Hackathon", "Day-long build", EventType.TECHNICAL, 100,
                EVENT_START, EVENT_START.plus(Duration.ofHours(6)));
        return EventRequest.submit(coding, hall, details, SUBMITTED_AT);
    }

    private EventRequest pendingDean() {
        EventRequest request = submitted();
        request.approve(venueOffice, BEFORE_ESCALATION);
        return request;
    }

    // ---- Backup approver ----------------------------------------------------

    @Test
    void backupApproverCannotActBeforeEscalation() {
        EventRequest request = pendingDean();

        assertThrows(NotPermittedException.class, () -> request.approve(backup, BEFORE_ESCALATION));
        assertThrows(NotPermittedException.class, () -> request.reject(backup, "no", BEFORE_ESCALATION));
        assertEquals(RequestStatus.PENDING_DEAN, request.getStatus());
    }

    @Test
    void backupApproverGivesFinalApprovalWhenTheDeanIsSilent() {
        EventRequest request = pendingDean();

        request.approve(backup, ESCALATED);

        assertEquals(RequestStatus.APPROVED, request.getStatus());
        assertTrue(request.isAuthorized());
        RequestAction last = request.getHistory().get(request.getHistory().size() - 1);
        assertEquals(backup, last.getActor(), "the audit trail shows who actually decided");
    }

    @Test
    void backupApproverCanRejectAnEscalatedRequest() {
        EventRequest request = pendingDean();

        request.reject(backup, "Clashes with the convocation", ESCALATED);

        assertEquals(RequestStatus.REJECTED, request.getStatus());
        assertEquals(PermissionStatus.DENIED, request.permissionStatus(ESCALATED));
    }

    @Test
    void backupApproverCanAlsoStepInForASilentVenueOffice() {
        EventRequest request = submitted();

        request.approve(backup, ESCALATED);
        assertEquals(RequestStatus.PENDING_DEAN, request.getStatus(), "confirms the hall; permission still needed");
        assertFalse(request.isAuthorized());

        request.approve(backup, ESCALATED);
        assertEquals(RequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void regularReviewersKeepTheirAuthorityAfterEscalation() {
        EventRequest request = pendingDean();

        request.approve(dean, ESCALATED);

        assertEquals(RequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void escalationDoesNotLetOtherRolesDecide() {
        EventRequest atVenue = submitted();
        assertThrows(NotPermittedException.class, () -> atVenue.approve(dean, ESCALATED));
        assertThrows(NotPermittedException.class, () -> atVenue.approve(coding, ESCALATED));

        EventRequest atDean = pendingDean();
        assertThrows(NotPermittedException.class, () -> atDean.approve(venueOffice, ESCALATED));
    }

    @Test
    void backupApproverCannotSubmitOrCancel() {
        EventDetails details = new EventDetails("x", "y", EventType.OTHER, null,
                EVENT_START, EVENT_START.plus(Duration.ofHours(1)));
        assertThrows(NotPermittedException.class, () -> EventRequest.submit(backup, hall, details, SUBMITTED_AT));

        EventRequest request = pendingDean();
        assertThrows(NotPermittedException.class, () -> request.cancel(backup, ESCALATED));
    }

    @Test
    void backupApproverCannotDecideAfterTheEventStarts() {
        EventRequest request = pendingDean();

        assertThrows(IllegalTransitionException.class, () -> request.approve(backup, EVENT_START));
    }

    @Test
    void canBeReviewedByShowsButtonsOnlyToPeopleWhoMayDecide() {
        EventRequest atVenue = submitted();
        assertTrue(atVenue.canBeReviewedBy(venueOffice, BEFORE_ESCALATION));
        assertFalse(atVenue.canBeReviewedBy(dean, BEFORE_ESCALATION));
        assertFalse(atVenue.canBeReviewedBy(backup, BEFORE_ESCALATION));
        assertTrue(atVenue.canBeReviewedBy(backup, ESCALATED));
        assertFalse(atVenue.canBeReviewedBy(coding, ESCALATED));

        EventRequest atDean = pendingDean();
        assertTrue(atDean.canBeReviewedBy(dean, BEFORE_ESCALATION));
        assertFalse(atDean.canBeReviewedBy(venueOffice, BEFORE_ESCALATION));
        assertFalse(atDean.canBeReviewedBy(dean, EVENT_START), "no decisions once the event has started");
    }

    // ---- Stored expiry ------------------------------------------------------

    @Test
    void expireMovesAPendingRequestToExpiredAsASystemAction() {
        EventRequest request = pendingDean();

        request.expire(EVENT_START);

        assertEquals(RequestStatus.EXPIRED, request.getStatus());
        assertFalse(request.getStatus().holdsVenue(), "the database slot is released");
        RequestAction last = request.getHistory().get(request.getHistory().size() - 1);
        assertEquals(ActionType.EXPIRED, last.getAction());
        assertNull(last.getActor(), "no person decided this");
        assertEquals(RequestStatus.PENDING_DEAN, last.getFromStatus());
    }

    @Test
    void storedExpiredRequestReportsExpiredEverywhere() {
        EventRequest request = pendingDean();
        request.expire(EVENT_START);
        Instant later = EVENT_START.plus(Duration.ofDays(1));

        assertTrue(request.isExpired(later));
        assertEquals(PermissionStatus.EXPIRED, request.permissionStatus(later));
        assertEquals(HallStatus.RELEASED, request.hallStatus(later));
        assertFalse(request.isAuthorized());
        assertFalse(request.isLivePending(later));
        assertThrows(IllegalTransitionException.class, () -> request.approve(dean, later));
        assertThrows(IllegalTransitionException.class, () -> request.approve(backup, later));
        assertThrows(IllegalTransitionException.class, () -> request.cancel(coding, later));
    }

    @Test
    void cannotExpireBeforeTheStartOrExpireAFinishedRequest() {
        EventRequest early = pendingDean();
        assertThrows(IllegalTransitionException.class, () -> early.expire(EVENT_START.minusSeconds(1)));

        EventRequest approved = pendingDean();
        approved.approve(dean, ESCALATED);
        assertThrows(IllegalTransitionException.class, () -> approved.expire(EVENT_START));

        EventRequest expired = pendingDean();
        expired.expire(EVENT_START);
        assertThrows(IllegalTransitionException.class, () -> expired.expire(EVENT_START));
    }

    // ---- Authorization guard ------------------------------------------------

    @Test
    void onlyFinalApprovalPassesTheAuthorizationGuard() {
        EventRequest tentative = submitted();
        assertThrows(NotPermittedException.class, tentative::requireAuthorized);

        EventRequest hallConfirmed = pendingDean();
        assertEquals(HallStatus.CONFIRMED, hallConfirmed.hallStatus(ESCALATED));
        assertThrows(NotPermittedException.class, hallConfirmed::requireAuthorized,
                "a confirmed hall alone must not authorize the event");

        EventRequest expired = pendingDean();
        expired.expire(EVENT_START);
        assertThrows(NotPermittedException.class, expired::requireAuthorized);

        EventRequest approved = pendingDean();
        approved.approve(dean, ESCALATED);
        approved.requireAuthorized();
    }
}