package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.IllegalTransitionException;
import com.eventdesk.common.InvalidRequestException;
import com.eventdesk.common.NotPermittedException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import com.eventdesk.venue.Venue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the request lifecycle: no Spring, no database.
 * Venue clashes are covered separately by the database integration test,
 * because the exclusion constraint is what enforces them.
 */
class EventRequestTest {

    // 24 Oct 2026, 10:00 IST
    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    // 35 days ahead: comfortably past the 30-day notice rule.
    private static final Instant EVENT_START = NOW.plus(Duration.ofDays(35));
    private static final Instant EVENT_END = EVENT_START.plus(Duration.ofHours(3));

    private final Club codingClub = new Club("Coding Club", "codingclub@example.org");
    private final Club roboticsClub = new Club("Robotics Club", "robotics@example.org");
    private final Venue seminarHall = new Venue("Seminar Hall A", "Academic Block 1", 150);

    private final AppUser codingCoordinator = new AppUser("coding@example.org", "hash", "Coding", Role.CLUB, codingClub);
    private final AppUser roboticsCoordinator = new AppUser("robotics@example.org", "hash", "Robotics", Role.CLUB, roboticsClub);
    private final AppUser venueOffice = new AppUser("venue@example.org", "hash", "Venue Office", Role.VENUE_OFFICE, null);
    private final AppUser dean = new AppUser("dean@example.org", "hash", "Dean", Role.DEAN, null);

    private EventDetails details(int attendance, Instant start, Instant end) {
        return new EventDetails("  Intro to CP  ", "Problem solving session", EventType.TECHNICAL, attendance, start, end);
    }

    private EventDetails validDetails() {
        return details(100, EVENT_START, EVENT_END);
    }

    private EventRequest submitted() {
        return EventRequest.submit(codingCoordinator, seminarHall, validDetails(), NOW);
    }

    private EventRequest pendingDean() {
        EventRequest request = submitted();
        request.approve(venueOffice, NOW);
        return request;
    }

    // ---- Submitting ---------------------------------------------------------

    @Test
    void submittedRequestWaitsForVenueOfficeAndBelongsToSubmittersClub() {
        EventRequest request = submitted();

        assertEquals(RequestStatus.PENDING_VENUE, request.getStatus());
        assertTrue(request.getClub().sameAs(codingClub));
        assertEquals("Intro to CP", request.getTitle(), "title should be trimmed");
        assertEquals(1, request.getHistory().size());
        RequestAction first = request.getHistory().get(0);
        assertEquals(ActionType.SUBMITTED, first.getAction());
        assertNull(first.getFromStatus());
        assertEquals(RequestStatus.PENDING_VENUE, first.getToStatus());
    }

    @Test
    void staffCannotSubmitRequests() {
        assertThrows(NotPermittedException.class,
                () -> EventRequest.submit(venueOffice, seminarHall, validDetails(), NOW));
        assertThrows(NotPermittedException.class,
                () -> EventRequest.submit(dean, seminarHall, validDetails(), NOW));
    }

    @Test
    void eventMustEndAfterItStarts() {
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(
                codingCoordinator, seminarHall, details(100, EVENT_END, EVENT_START), NOW));
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(
                codingCoordinator, seminarHall, details(100, EVENT_START, EVENT_START), NOW));
    }

    @Test
    void requestsNeedThirtyDaysNotice() {
        Instant exactlyThirtyDays = NOW.plus(EventRequest.MIN_NOTICE);
        EventRequest onTheLine = EventRequest.submit(codingCoordinator, seminarHall,
                details(100, exactlyThirtyDays, exactlyThirtyDays.plus(Duration.ofHours(2))), NOW);
        assertEquals(RequestStatus.PENDING_VENUE, onTheLine.getStatus(), "exactly 30 days is enough");

        Instant oneMinuteShort = exactlyThirtyDays.minusSeconds(60);
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(codingCoordinator, seminarHall,
                details(100, oneMinuteShort, oneMinuteShort.plus(Duration.ofHours(2))), NOW));

        Instant oneHourAgo = NOW.minus(Duration.ofHours(1));
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(codingCoordinator, seminarHall,
                details(100, oneHourAgo, NOW.plus(Duration.ofHours(1))), NOW));
    }

    @Test
    void bookingCanBeAtMostTwelveHours() {
        Instant twelveHoursLater = EVENT_START.plus(Duration.ofHours(12));
        EventRequest exactlyTwelve = EventRequest.submit(
                codingCoordinator, seminarHall, details(100, EVENT_START, twelveHoursLater), NOW);
        assertEquals(RequestStatus.PENDING_VENUE, exactlyTwelve.getStatus());

        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(codingCoordinator, seminarHall,
                details(100, EVENT_START, twelveHoursLater.plusSeconds(60)), NOW));
    }

    @Test
    void moreAttendeesThanSeatsIsAllowedButFlagged() {
        EventRequest full = EventRequest.submit(codingCoordinator, seminarHall,
                details(150, EVENT_START, EVENT_END), NOW);
        assertEquals(150, full.getExpectedAttendance());
        assertFalse(full.exceedsVenueCapacity(), "exactly at capacity is fine");

        EventRequest overflowing = EventRequest.submit(codingCoordinator, seminarHall,
                details(151, EVENT_START, EVENT_END), NOW);
        assertEquals(RequestStatus.PENDING_VENUE, overflowing.getStatus());
        assertTrue(overflowing.exceedsVenueCapacity());
    }

    @Test
    void attendanceMustBePositiveWhenGiven() {
        assertThrows(InvalidRequestException.class, () -> EventRequest.submit(
                codingCoordinator, seminarHall, details(0, EVENT_START, EVENT_END), NOW));
    }

    @Test
    void technicalEventsAndWorkshopsNeedAnAttendanceEstimate() {
        for (EventType counted : new EventType[] {EventType.TECHNICAL, EventType.WORKSHOP}) {
            EventDetails noCount = new EventDetails("Title", "desc", counted, null, EVENT_START, EVENT_END);
            assertThrows(InvalidRequestException.class,
                    () -> EventRequest.submit(codingCoordinator, seminarHall, noCount, NOW));
        }
    }

    @Test
    void otherEventTypesCanOmitAttendance() {
        for (EventType uncounted : new EventType[] {EventType.CULTURAL, EventType.WEBINAR, EventType.OTHER}) {
            EventDetails noCount = new EventDetails("Title", "desc", uncounted, null, EVENT_START, EVENT_END);
            EventRequest request = EventRequest.submit(codingCoordinator, seminarHall, noCount, NOW);

            assertNull(request.getExpectedAttendance());
            assertFalse(request.exceedsVenueCapacity());
        }
    }

    @Test
    void titleAndDescriptionAreRequired() {
        EventDetails noTitle = new EventDetails("   ", "desc", EventType.WORKSHOP, 10, EVENT_START, EVENT_END);
        EventDetails noDescription = new EventDetails("Title", null, EventType.WORKSHOP, 10, EVENT_START, EVENT_END);

        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(codingCoordinator, seminarHall, noTitle, NOW));
        assertThrows(InvalidRequestException.class,
                () -> EventRequest.submit(codingCoordinator, seminarHall, noDescription, NOW));
    }

    // ---- Two-stage approval -------------------------------------------------

    @Test
    void venueOfficeThenDeanApprovalGivesFinalApproval() {
        EventRequest request = submitted();

        request.approve(venueOffice, NOW);
        assertEquals(RequestStatus.PENDING_DEAN, request.getStatus());

        request.approve(dean, NOW);
        assertEquals(RequestStatus.APPROVED, request.getStatus());

        List<RequestAction> history = request.getHistory();
        assertEquals(3, history.size());
        assertEquals(venueOffice, history.get(1).getActor());
        assertEquals(RequestStatus.PENDING_VENUE, history.get(1).getFromStatus());
        assertEquals(dean, history.get(2).getActor());
        assertEquals(RequestStatus.APPROVED, history.get(2).getToStatus());
    }

    @Test
    void deanCannotSkipTheVenueOffice() {
        EventRequest request = submitted();

        assertThrows(NotPermittedException.class, () -> request.approve(dean, NOW));
        assertEquals(RequestStatus.PENDING_VENUE, request.getStatus());
    }

    @Test
    void venueOfficeCannotGiveTheDeansApproval() {
        EventRequest request = pendingDean();

        assertThrows(NotPermittedException.class, () -> request.approve(venueOffice, NOW));
        assertEquals(RequestStatus.PENDING_DEAN, request.getStatus());
    }

    @Test
    void clubsCannotApproveOrRejectAnyRequest() {
        EventRequest request = submitted();

        assertThrows(NotPermittedException.class, () -> request.approve(codingCoordinator, NOW));
        assertThrows(NotPermittedException.class, () -> request.reject(codingCoordinator, "no", NOW));
        assertThrows(NotPermittedException.class, () -> request.approve(roboticsCoordinator, NOW));
        assertEquals(1, request.getHistory().size(), "failed attempts must not be recorded as actions");
    }

    @Test
    void finishedRequestsCannotBeReviewedAgain() {
        EventRequest approved = pendingDean();
        approved.approve(dean, NOW);
        assertThrows(IllegalTransitionException.class, () -> approved.approve(dean, NOW));
        assertThrows(IllegalTransitionException.class, () -> approved.reject(dean, "changed my mind", NOW));

        EventRequest rejected = submitted();
        rejected.reject(venueOffice, "Hall closed", NOW);
        assertThrows(IllegalTransitionException.class, () -> rejected.approve(venueOffice, NOW));
        assertThrows(IllegalTransitionException.class, () -> rejected.approve(dean, NOW));
    }

    @Test
    void cannotApproveAnEventThatHasAlreadyStarted() {
        EventRequest request = submitted();
        Instant duringEvent = EVENT_START.plus(Duration.ofMinutes(30));

        assertThrows(IllegalTransitionException.class, () -> request.approve(venueOffice, duringEvent));
    }

    // ---- Rejection ----------------------------------------------------------

    @Test
    void rejectionNeedsAReasonAndStoresIt() {
        EventRequest atVenue = submitted();
        assertThrows(InvalidRequestException.class, () -> atVenue.reject(venueOffice, "   ", NOW));
        assertThrows(InvalidRequestException.class, () -> atVenue.reject(venueOffice, null, NOW));
        assertEquals(RequestStatus.PENDING_VENUE, atVenue.getStatus());

        atVenue.reject(venueOffice, "  Hall booked for exams  ", NOW);
        assertEquals(RequestStatus.REJECTED, atVenue.getStatus());
        assertEquals("Hall booked for exams", atVenue.getRejectionReason());
        assertEquals("Hall booked for exams", atVenue.getHistory().get(1).getRemark());

        EventRequest atDean = pendingDean();
        atDean.reject(dean, "Clashes with the convocation", NOW);
        assertEquals(RequestStatus.REJECTED, atDean.getStatus());
        assertEquals(RequestStatus.PENDING_DEAN, atDean.getHistory().get(2).getFromStatus());
    }

    @Test
    void rejectionReasonHasAMaximumLength() {
        EventRequest request = submitted();
        String tooLong = "x".repeat(EventRequest.MAX_REMARK_LENGTH + 1);

        assertThrows(InvalidRequestException.class, () -> request.reject(venueOffice, tooLong, NOW));
    }

    // ---- Cancellation -------------------------------------------------------

    @Test
    void owningClubCanCancelAtAnyLiveStage() {
        EventRequest pending = submitted();
        pending.cancel(codingCoordinator, NOW);
        assertEquals(RequestStatus.CANCELLED, pending.getStatus());

        EventRequest approved = pendingDean();
        approved.approve(dean, NOW);
        approved.cancel(codingCoordinator, NOW);
        assertEquals(RequestStatus.CANCELLED, approved.getStatus());
        assertEquals(ActionType.CANCELLED, approved.getHistory().get(3).getAction());
    }

    @Test
    void otherClubsAndStaffCannotCancel() {
        EventRequest request = submitted();

        assertThrows(NotPermittedException.class, () -> request.cancel(roboticsCoordinator, NOW));
        assertThrows(NotPermittedException.class, () -> request.cancel(venueOffice, NOW));
        assertThrows(NotPermittedException.class, () -> request.cancel(dean, NOW));
        assertEquals(RequestStatus.PENDING_VENUE, request.getStatus());
    }

    @Test
    void cannotCancelTwiceOrCancelARejectedRequest() {
        EventRequest cancelled = submitted();
        cancelled.cancel(codingCoordinator, NOW);
        assertThrows(IllegalTransitionException.class, () -> cancelled.cancel(codingCoordinator, NOW));

        EventRequest rejected = submitted();
        rejected.reject(venueOffice, "Hall closed", NOW);
        assertThrows(IllegalTransitionException.class, () -> rejected.cancel(codingCoordinator, NOW));
    }

    @Test
    void cannotCancelAnEventThatHasStarted() {
        EventRequest request = submitted();

        assertThrows(IllegalTransitionException.class, () -> request.cancel(codingCoordinator, EVENT_START));
    }

    // ---- Status rules -------------------------------------------------------

    @Test
    void onlyLiveRequestsHoldTheVenue() {
        assertTrue(RequestStatus.PENDING_VENUE.holdsVenue());
        assertTrue(RequestStatus.PENDING_DEAN.holdsVenue());
        assertTrue(RequestStatus.APPROVED.holdsVenue());
        assertFalse(RequestStatus.REJECTED.holdsVenue());
        assertFalse(RequestStatus.CANCELLED.holdsVenue());
    }

    @Test
    void ownershipIsByClub() {
        EventRequest request = submitted();

        assertTrue(request.isOwnedBy(codingCoordinator));
        assertFalse(request.isOwnedBy(roboticsCoordinator));
        assertFalse(request.isOwnedBy(venueOffice));
    }
}