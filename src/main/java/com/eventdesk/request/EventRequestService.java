package com.eventdesk.request;

import com.eventdesk.common.NotFoundException;
import com.eventdesk.common.VenueUnavailableException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.AppUserRepository;
import com.eventdesk.venue.Venue;
import com.eventdesk.venue.VenueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;

import java.time.Clock;

/**
 * Applies reviewer and club actions to a request.
 *
 * <p>Every method is one transaction: load the request and the acting user,
 * let the entity apply its rules, save. Status, timestamps, version and the
 * new history row are written together or not at all, so the audit trail can
 * never disagree with the request. If two people act at the same moment, the
 * @Version check on EventRequest makes the second save fail and roll back.
 *
 * <p>Methods take the actor's id, not an AppUser: the signed-in user lives in
 * the session as a snapshot (CurrentUser), so a fresh AppUser is loaded inside
 * the transaction. That way the role and club are current, and lazy
 * relationships work.
 *
 * <p>saveAndFlush (instead of save) makes a version conflict surface inside
 * the method, where the web layer can turn it into a clear message.
 */
@Service
public class EventRequestService {

    private final EventRequestRepository requests;
    private final AppUserRepository users;
    private final VenueRepository venues;
    private final Clock clock;
    private final ZoneId zone;

    public EventRequestService(EventRequestRepository requests, AppUserRepository users,
                               VenueRepository venues, Clock clock, ZoneId zone) {
        this.requests = requests;
        this.users = users;
        this.venues = venues;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * A club submits a request. The entity checks the rules; the database's
     * exclusion constraint checks the slot is free. Only the database can do
     * that safely when two clubs submit at the same moment, so there is no
     * "is it free?" query beforehand that could race.
     */
    @Transactional
    public long submit(long actorId, long venueId, EventDetails details) {
        AppUser actor = findUser(actorId);
        Venue venue = venues.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue " + venueId + " was not found"));

        OperatingHours.check(details, zone);
        EventRequest request = EventRequest.submit(actor, venue, details, clock.instant());

        try {
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            if (isVenueClash(e)) {
                throw new VenueUnavailableException(venue.getName());
            }
            throw e;
        }
        return request.getId();
    }

    @Transactional
    public void approve(long requestId, long actorId) {
        EventRequest request = findRequest(requestId);
        request.approve(findUser(actorId), clock.instant());
        requests.saveAndFlush(request);
    }

    @Transactional
    public void reject(long requestId, long actorId, String reason) {
        EventRequest request = findRequest(requestId);
        request.reject(findUser(actorId), reason, clock.instant());
        requests.saveAndFlush(request);
    }

    @Transactional
    public void cancel(long requestId, long actorId) {
        EventRequest request = findRequest(requestId);
        request.cancel(findUser(actorId), clock.instant());
        requests.saveAndFlush(request);
    }

    @Transactional
    public void remind(long requestId, long actorId) {
        EventRequest request = findRequest(requestId);
        request.remind(findUser(actorId), clock.instant());
        requests.saveAndFlush(request);
    }

    /** The exclusion constraint's name appears in PostgreSQL's error message. */
    private static boolean isVenueClash(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getMessage() != null && cause.getMessage().contains("no_overlapping_venue_bookings");
    }

    private EventRequest findRequest(long requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Event request " + requestId + " was not found"));
    }

    private AppUser findUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " was not found"));
    }
}