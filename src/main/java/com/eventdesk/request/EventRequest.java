package com.eventdesk.request;

import com.eventdesk.club.Club;
import com.eventdesk.common.IllegalTransitionException;
import com.eventdesk.common.InvalidRequestException;
import com.eventdesk.common.NotPermittedException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import com.eventdesk.venue.Venue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A club's request to hold an event in a venue: one record that replaces the
 * separate emails to the hall reservation office and to the dean.
 *
 * <p>All status changes go through the methods on this class. Each method
 * checks who is acting and what the current status is, then records an entry
 * in {@link #getHistory()}. The service layer cannot skip a stage or approve
 * on someone else's behalf, because no setters exist for status.
 *
 * <p>Venue clashes are NOT checked here: the database's exclusion constraint
 * is the single source of truth for that, because only it is safe when two
 * clubs submit at the same moment.
 *
 * <p>{@code now} is passed in rather than read from the clock so that the
 * rules are deterministic and easy to unit test.
 */
@Entity
@Table(name = "event_requests")
public class EventRequest {

    /** College rule: clubs must request at least this long before the event. */
    public static final Duration MIN_NOTICE = Duration.ofDays(30);
    static final Duration MAX_DURATION = Duration.ofHours(12);
    /** Extended technical events (e.g. 24-hour hackathons) with department approval. */
    public static final Duration MAX_EXTENDED_DURATION = Duration.ofHours(36);
    static final int MAX_APPROVAL_REF_LENGTH = 100;
    static final int MAX_TYPE_DETAIL_LENGTH = 60;
    static final int MAX_REMARK_LENGTH = 500;
    /** A pending request is overdue once it has waited this long at its current stage. */
    public static final Duration OVERDUE_AFTER = Duration.ofDays(2);
    /** A club can send at most one reminder per request in this window. */
    public static final Duration REMINDER_COOLDOWN = Duration.ofHours(24);
    /**
     * A request still pending this close to the event is escalated: it goes to
     * the backup approver's queue, and the backup approver may decide it.
     */
    public static final Duration ESCALATION_WINDOW = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private AppUser submittedBy;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    /** What kind of event an "Other" event is, e.g. "Alumni meet". Null for every other type. */
    @Column(name = "event_type_detail", length = MAX_TYPE_DETAIL_LENGTH)
    private String eventTypeDetail;
    /** Null when not given (allowed only for types that don't require a head count). */
    @Column(name = "expected_attendance")
    private Integer expectedAttendance;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "rejection_reason", length = MAX_REMARK_LENGTH)
    private String rejectionReason;

    /** Department approval for an extended technical event (e.g. HOD letter number); otherwise null. */
    @Column(name = "department_approval_ref", length = MAX_APPROVAL_REF_LENGTH)
    private String departmentApprovalRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the request entered its current status. Overdue is measured from here. */
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    /**
     * Optimistic locking: if two reviewers act on the same request at once,
     * the second save fails instead of silently overwriting the first.
     */
    @Version
    private Long version;

    @OneToMany(mappedBy = "eventRequest", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC")
    private List<RequestAction> history = new ArrayList<>();

    protected EventRequest() {
        // for JPA
    }

    private EventRequest(AppUser submitter, Venue venue, EventDetails details, Instant now) {
        this.club = submitter.getClub();
        this.venue = venue;
        this.submittedBy = submitter;
        this.title = details.title().strip();
        this.description = details.description().strip();
        this.eventType = details.eventType();
        this.eventTypeDetail = details.eventType() == EventType.OTHER ? details.eventTypeDetail().strip() : null;
        this.expectedAttendance = details.expectedAttendance();
        this.startsAt = details.startsAt();
        this.endsAt = details.endsAt();
        this.departmentApprovalRef = details.allowsExtendedHours() ? details.departmentApprovalRef().strip() : null;
        this.status = RequestStatus.PENDING_VENUE;
        this.createdAt = now;
        this.updatedAt = now;
        this.statusChangedAt = now;
    }

    /** A club coordinator asks for a venue. The request starts in PENDING_VENUE. */
    public static EventRequest submit(AppUser submitter, Venue venue, EventDetails details, Instant now) {
        if (submitter.getRole() != Role.CLUB) {
            throw new NotPermittedException("Only club coordinators can submit event requests");
        }
        validate(details, now);

        EventRequest request = new EventRequest(submitter, venue, details, now);
        request.record(submitter, ActionType.SUBMITTED, null, RequestStatus.PENDING_VENUE, null, now);
        return request;
    }

    private static void validate(EventDetails details, Instant now) {
        if (isBlank(details.title())) {
            throw new InvalidRequestException("Please give the event a title");
        }
        if (isBlank(details.description())) {
            throw new InvalidRequestException("Please describe the event so reviewers know what they are approving");
        }
        if (details.eventType() == null) {
            throw new InvalidRequestException("Please choose an event type");
        }
        if (details.eventType() == EventType.OTHER) {
            if (isBlank(details.eventTypeDetail())) {
                throw new InvalidRequestException("For an \"Other\" event, say what kind of event it is");
            }
            if (details.eventTypeDetail().strip().length() > MAX_TYPE_DETAIL_LENGTH) {
                throw new InvalidRequestException("Keep the kind of event under "
                        + MAX_TYPE_DETAIL_LENGTH + " characters");
            }
        }
        if (details.startsAt() == null || details.endsAt() == null) {
            throw new InvalidRequestException("Please choose a start and end time");
        }
        if (!details.endsAt().isAfter(details.startsAt())) {
            throw new InvalidRequestException("The event must end after it starts");
        }
        if (details.startsAt().isBefore(now.plus(MIN_NOTICE))) {
            throw new InvalidRequestException("Requests must be submitted at least "
                    + MIN_NOTICE.toDays() + " days before the event");
        }
        validateDuration(details);
        validateAttendance(details.eventType(), details.expectedAttendance());
    }
    /**
     * Standard events last at most 12 hours. Technical events may run up to
     * 36 hours (24-hour hackathons) if the club gives the department's
     * approval reference; reviewers then see the event flagged as extended.
     */
    private static void validateDuration(EventDetails details) {
        Duration length = Duration.between(details.startsAt(), details.endsAt());
        if (details.departmentApprovalRef() != null
                && details.departmentApprovalRef().strip().length() > MAX_APPROVAL_REF_LENGTH) {
            throw new InvalidRequestException("The department approval reference can be at most "
                    + MAX_APPROVAL_REF_LENGTH + " characters");
        }
        if (length.compareTo(MAX_DURATION) <= 0) {
            return;
        }
        if (details.eventType() != EventType.TECHNICAL) {
            throw new InvalidRequestException("A booking can be at most " + MAX_DURATION.toHours()
                    + " hours long. Only technical events (such as hackathons) can run longer.");
        }
        if (!details.allowsExtendedHours()) {
            throw new InvalidRequestException("Technical events longer than " + MAX_DURATION.toHours()
                    + " hours need the department's approval. Enter its approval reference.");
        }
        if (length.compareTo(MAX_EXTENDED_DURATION) > 0) {
            throw new InvalidRequestException("Even extended technical events can be at most "
                    + MAX_EXTENDED_DURATION.toHours() + " hours long");
        }
    }
    /**
     * The expected count is an estimate, required for event types the college
     * counts (technical events and workshops) and optional otherwise.
     * It is deliberately NOT capped at the venue's capacity: a big event may
     * need more than one hall, so going over capacity is flagged to reviewers
     * (see {@link #exceedsVenueCapacity()}) instead of being rejected.
     */
    private static void validateAttendance(EventType type, Integer attendance) {
        if (attendance == null) {
            if (type.requiresAttendance()) {
                throw new InvalidRequestException("Please give the expected attendance for a "
                        + type.label().toLowerCase());
            }
            return;
        }
        if (attendance < 1) {
            throw new InvalidRequestException("Expected attendance must be at least 1");
        }
    }

    /**
     * The reviewer for the current stage approves. The venue office moves the
     * request to the dean; the dean gives final approval. Once a request is
     * escalated, the backup approver may act in place of either.
     */
    public void approve(AppUser actor, Instant now) {
        requireNotStarted(now, "approved");
        requireCurrentReviewer(actor, now);
        transition(actor, ActionType.APPROVED, status.afterApproval(), null, now);
    }

    /** The reviewer for the current stage rejects, with a reason the club will see. */
    public void reject(AppUser actor, String reason, Instant now) {
        requireNotStarted(now, "rejected");
        requireCurrentReviewer(actor, now);
        if (isBlank(reason)) {
            throw new InvalidRequestException("Please give a reason so the club knows what to change");
        }
        String trimmed = reason.strip();
        if (trimmed.length() > MAX_REMARK_LENGTH) {
            throw new InvalidRequestException("The reason can be at most " + MAX_REMARK_LENGTH + " characters");
        }
        this.rejectionReason = trimmed;
        transition(actor, ActionType.REJECTED, RequestStatus.REJECTED, trimmed, now);
    }

    /** The club that submitted the request withdraws it, which frees the venue slot. */
    public void cancel(AppUser actor, Instant now) {
        if (!isOwnedBy(actor)) {
            throw new NotPermittedException("Only the club that submitted this request can cancel it");
        }
        if (!status.holdsVenue()) {
            throw new IllegalTransitionException("A request that is " + status.label().toLowerCase() + " cannot be cancelled");
        }
        requireNotStarted(now, "cancelled");
        transition(actor, ActionType.CANCELLED, RequestStatus.CANCELLED, null, now);
    }

    /**
     * System action: a request still pending when its event starts becomes
     * EXPIRED. This changes the stored status, which takes it out of the
     * database's overlap constraint and so releases the slot for real.
     * Run by a scheduled sweep; {@link #isExpired} covers the time between sweeps.
     */
    public void expire(Instant now) {
        if (status.reviewer().isEmpty()) {
            throw new IllegalTransitionException("Only a pending request can expire");
        }
        if (startsAt.isAfter(now)) {
            throw new IllegalTransitionException("A request can only expire once its event has started");
        }
        transition(null, ActionType.EXPIRED, RequestStatus.EXPIRED,
                "No decision was made before the event started", now);
    }

    /**
     * Guard for anything that lets the event go ahead (event pass, OD list,
     * certificates, marking the calendar as confirmed). A confirmed hall alone
     * is not enough: only final approval passes.
     */
    public void requireAuthorized() {
        if (!isAuthorized()) {
            throw new NotPermittedException("This event has not been approved, so it cannot go ahead. "
                    + "Current status: " + status.label());
        }
    }

    /**
     * The submitting club nudges the current reviewer. Allowed once the request
     * is overdue or escalated, and at most once per {@link #REMINDER_COOLDOWN},
     * so reminders stay meaningful instead of becoming spam. Each reminder is
     * recorded in the history.
     */
    public void remind(AppUser actor, Instant now) {
        if (!isOwnedBy(actor)) {
            throw new NotPermittedException("Only the club that submitted this request can send a reminder");
        }
        if (status.reviewer().isEmpty()) {
            throw new IllegalTransitionException("A request that is " + status.label().toLowerCase() + " has no pending review to remind about");
        }
        requireNotStarted(now, "followed up");
        if (!isOverdue(now) && !isEscalated(now)) {
            throw new IllegalTransitionException("You can send a reminder once the request has waited "
                    + OVERDUE_AFTER.toDays() + " days at this stage, or when the event is less than "
                    + ESCALATION_WINDOW.toDays() + " days away");
        }
        if (lastReminderAt != null && now.isBefore(lastReminderAt.plus(REMINDER_COOLDOWN))) {
            throw new IllegalTransitionException("A reminder was already sent in the last "
                    + REMINDER_COOLDOWN.toHours() + " hours");
        }
        this.lastReminderAt = now;
        this.updatedAt = now;
        record(actor, ActionType.REMINDED, status, status, null, now);
    }

    /** Whether the club may send a reminder right now. Used to show or hide the button. */
    public boolean canRemind(AppUser actor, Instant now) {
        return isOwnedBy(actor)
                && (isOverdue(now) || isEscalated(now))
                && startsAt.isAfter(now)
                && (lastReminderAt == null || !now.isBefore(lastReminderAt.plus(REMINDER_COOLDOWN)));
    }

    /**
     * More people are expected than the venue seats, so the club may need an
     * additional venue. Shown as a warning to the club and the reviewers.
     */
    public boolean exceedsVenueCapacity() {
        return expectedAttendance != null && expectedAttendance > venue.getCapacity();
    }

    /** Pending, not yet expired, and has waited more than {@link #OVERDUE_AFTER} at its current stage. */
    public boolean isOverdue(Instant now) {
        return isLivePending(now) && now.isAfter(statusChangedAt.plus(OVERDUE_AFTER));
    }

    // ---- Permission policy ---------------------------------------------------
    //
    // 1. Only final approval authorizes the event. Pending is never permission.
    // 2. The club is warned while permission is missing (permissionStatus).
    // 3. Pending within ESCALATION_WINDOW of the event -> escalated.
    // 4. Still pending at the start time -> expired: out of the review queues,
    //    all actions blocked (requireNotStarted), never treated as approved.
    //    A scheduled sweep stores EXPIRED via expire(), which frees the slot in
    //    the database. Until the sweep runs (e.g. the server was asleep),
    //    isExpired() already treats the request as expired from the clock.

    /** True only with final approval. Pending, expired, rejected or cancelled requests are not permission. */
    public boolean isAuthorized() {
        return status == RequestStatus.APPROVED;
    }

    /** Waiting for a reviewer and the event hasn't started. */
    public boolean isLivePending(Instant now) {
        return status.reviewer().isPresent() && startsAt.isAfter(now);
    }

    /**
     * No decision was made before the event started: either already marked
     * EXPIRED by the sweep, or still pending past its start time.
     */
    public boolean isExpired(Instant now) {
        return status == RequestStatus.EXPIRED
                || (status.reviewer().isPresent() && !startsAt.isAfter(now));
    }

    /** Still pending within {@link #ESCALATION_WINDOW} of the event. */
    public boolean isEscalated(Instant now) {
        return isLivePending(now) && !now.isBefore(startsAt.minus(ESCALATION_WINDOW));
    }

    /** Whether the event may happen, as the club and reviewers should see it. */
    public PermissionStatus permissionStatus(Instant now) {
        return switch (status) {
            case APPROVED -> PermissionStatus.GRANTED;
            case REJECTED -> PermissionStatus.DENIED;
            case CANCELLED -> PermissionStatus.WITHDRAWN;
            case EXPIRED -> PermissionStatus.EXPIRED;
            case PENDING_VENUE, PENDING_DEAN ->
                    isExpired(now) ? PermissionStatus.EXPIRED : PermissionStatus.PENDING;
        };
    }

    /**
     * Whether the hall is held. A confirmed hall does not mean the event is
     * permitted. Policy: expiry releases the hall.
     */
    public HallStatus hallStatus(Instant now) {
        if (isExpired(now)) {
            return HallStatus.RELEASED;
        }
        return switch (status) {
            case PENDING_VENUE -> HallStatus.TENTATIVE;
            case PENDING_DEAN, APPROVED -> HallStatus.CONFIRMED;
            case REJECTED, CANCELLED, EXPIRED -> HallStatus.RELEASED;
        };
    }

    /** How long the request has been in its current status. */
    public Duration timeInCurrentStatus(Instant now) {
        return Duration.between(statusChangedAt, now);
    }

    /**
     * Whether this person may approve or reject right now: the regular
     * reviewer for the current stage, or the backup approver once escalated,
     * and only before the event starts. Mirrors the checks in approve/reject,
     * so pages show buttons only to people who can actually use them.
     */
    public boolean canBeReviewedBy(AppUser actor, Instant now) {
        if (!startsAt.isAfter(now)) {
            return false;
        }
        return status.reviewer()
                .map(reviewer -> actor.getRole() == reviewer
                        || (actor.getRole() == Role.BACKUP_APPROVER && isEscalated(now)))
                .orElse(false);
    }

    public boolean isOwnedBy(AppUser user) {
        return user.getRole() == Role.CLUB && club.sameAs(user.getClub());
    }

    /**
     * The regular reviewer for the current stage may act, and so may the
     * backup approver once the request is escalated.
     */
    private void requireCurrentReviewer(AppUser actor, Instant now) {
        Role reviewer = status.reviewer().orElseThrow(() -> new IllegalTransitionException(
                "This request is already " + status.label().toLowerCase() + " and can no longer be reviewed"));
        if (actor.getRole() == reviewer) {
            return;
        }
        if (actor.getRole() == Role.BACKUP_APPROVER) {
            if (isEscalated(now)) {
                return;
            }
            throw new NotPermittedException("The " + Role.BACKUP_APPROVER.label()
                    + " can only decide requests that have been escalated");
        }
        throw new NotPermittedException("At this stage only the " + reviewer.label() + " can review this request");
    }

    private void requireNotStarted(Instant now, String verb) {
        if (!startsAt.isAfter(now)) {
            throw new IllegalTransitionException("This event has already started, so it can no longer be " + verb);
        }
    }

    private void transition(AppUser actor, ActionType action, RequestStatus to, String remark, Instant now) {
        RequestStatus from = this.status;
        this.status = to;
        this.updatedAt = now;
        this.statusChangedAt = now;
        record(actor, action, from, to, remark, now);
    }

    private void record(AppUser actor, ActionType action, RequestStatus from, RequestStatus to,
                        String remark, Instant now) {
        history.add(new RequestAction(this, actor, action, from, to, remark, now));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long getId() {
        return id;
    }

    public Club getClub() {
        return club;
    }

    public Venue getVenue() {
        return venue;
    }

    public AppUser getSubmittedBy() {
        return submittedBy;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventType getEventType() {
        return eventType;
    }


    public String getEventTypeDetail() {
        return eventTypeDetail;
    }

    /** "Workshop", or for Other events what the club wrote, e.g. "Other: Alumni meet". */
    public String eventTypeLabel() {
        return eventTypeDetail == null ? eventType.label() : eventType.label() + ": " + eventTypeDetail;
    }

    public Integer getExpectedAttendance() {
        return expectedAttendance;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getDepartmentApprovalRef() {
        return departmentApprovalRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getLastReminderAt() {
        return lastReminderAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<RequestAction> getHistory() {
        return Collections.unmodifiableList(history);
    }
}