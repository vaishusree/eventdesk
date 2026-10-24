package com.eventdesk.request;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * List queries load the club and venue in the same SQL statement
 * (@EntityGraph), because every list shows them. Without it, each row would
 * trigger extra queries: the N+1 problem.
 */
public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {

    /** Shared calendar: requests holding a venue that haven't finished yet, soonest first. */
    @EntityGraph(attributePaths = {"club", "venue"})
    List<EventRequest> findByStatusInAndEndsAtAfterOrderByStartsAtAsc(Collection<RequestStatus> statuses, Instant after);

    /** Review queues: requests waiting at the given stages whose event hasn't started, longest-waiting first. */
    @EntityGraph(attributePaths = {"club", "venue"})
    List<EventRequest> findByStatusInAndStartsAtAfterOrderByStatusChangedAtAsc(
            Collection<RequestStatus> statuses, Instant after);

    /** Detail page: one request with everything it shows, history and actors included, in one query. */
    @EntityGraph(attributePaths = {"club", "venue", "submittedBy", "history", "history.actor"})
    Optional<EventRequest> findWithDetailsById(Long id);

    /** A club's own requests, newest event first. */
    @EntityGraph(attributePaths = {"club", "venue"})
    List<EventRequest> findByClubIdOrderByStartsAtDesc(Long clubId);
}