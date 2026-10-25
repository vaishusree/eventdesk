package com.eventdesk.request;

import com.eventdesk.common.NotFoundException;
import com.eventdesk.common.NotPermittedException;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.AppUserRepository;
import com.eventdesk.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.YearMonth;
/**
 * Read side: turns requests into page-ready summaries. Kept apart from
 * EventRequestService, which only changes data. readOnly lets the database
 * and Hibernate skip work they'd need for writes.
 */
@Service
@Transactional(readOnly = true)
public class RequestQueryService {

    private static final Set<RequestStatus> HOLDING =
            EnumSet.of(RequestStatus.PENDING_VENUE, RequestStatus.PENDING_DEAN, RequestStatus.APPROVED);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final Set<RequestStatus> PENDING =
            EnumSet.of(RequestStatus.PENDING_VENUE, RequestStatus.PENDING_DEAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final DateTimeFormatter END_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    /** Escalated first (event is close), then whoever has waited longest. */
    private static final Comparator<RequestSummary> URGENCY =
            Comparator.comparing(RequestSummary::escalated).reversed();

    private final EventRequestRepository requests;
    private final AppUserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    public RequestQueryService(EventRequestRepository requests, AppUserRepository users, Clock clock, ZoneId zone) {
        this.requests = requests;
        this.users = users;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * Everything that holds a venue from now on, grouped by date. Every club
     * sees every booking: this is what replaces emailing the office to ask.
     * Pending requests whose start has passed are left out (they're expired).
     */
    /**
     * One month of the shared calendar, optionally for a single venue. Every
     * club sees every booking: this is what replaces emailing the office to
     * ask. Pending requests whose start has passed are left out (expired).
     * Clubs can open only their own requests, so other clubs' chips aren't links.
     */
    public CalendarView calendar(YearMonth month, Long venueId, long viewerId) {
        AppUser viewer = findUser(viewerId);
        Instant now = clock.instant();
        Instant from = MonthGrid.firstVisibleDay(month).atStartOfDay(zone).toInstant();
        Instant to = MonthGrid.lastVisibleDay(month).plusDays(1).atStartOfDay(zone).toInstant();

        List<MonthGrid.Booking> bookings = new ArrayList<>();
        Map<String, List<RequestSummary>> byDate = new LinkedHashMap<>();
        for (EventRequest request : requests.findByStatusInAndStartsAtBeforeAndEndsAtAfterOrderByStartsAtAsc(HOLDING, to, from)) {
            if (request.isExpired(now) || (venueId != null && !venueId.equals(request.getVenue().getId()))) {
                continue;
            }
            RequestSummary summary = summarize(request, null, now);
            bookings.add(toBooking(request, summary, viewer));
            if (YearMonth.from(request.getStartsAt().atZone(zone)).equals(month)) {
                byDate.computeIfAbsent(summary.dateLabel(), d -> new ArrayList<>()).add(summary);
            }
        }
        List<CalendarDay> agenda = new ArrayList<>();
        byDate.forEach((date, list) -> agenda.add(new CalendarDay(date, list)));

        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new CalendarView(MonthGrid.of(month, today, bookings), agenda, venueId, YearMonth.from(today));
    }

    /** The month the calendar opens on: the one with the next booking, or this month if there is none. */
    public YearMonth defaultCalendarMonth() {
        Instant now = clock.instant();
        return requests.findFirstByStatusInAndEndsAtAfterOrderByStartsAtAsc(HOLDING, now)
                .map(r -> r.getStartsAt().isBefore(now) ? now : r.getStartsAt())
                .map(start -> YearMonth.from(start.atZone(zone)))
                .orElse(YearMonth.now(clock.withZone(zone)));
    }

    private MonthGrid.Booking toBooking(EventRequest request, RequestSummary summary, AppUser viewer) {
        ZonedDateTime start = request.getStartsAt().atZone(zone);
        // An event ending exactly at midnight doesn't occupy the next day.
        LocalDate lastDay = request.getEndsAt().minusNanos(1).atZone(zone).toLocalDate();
        boolean linkable = viewer.getRole() != Role.CLUB || request.isOwnedBy(viewer);
        MonthGrid.Entry entry = new MonthGrid.Entry(request.getId(), summary.title(), summary.clubName(),
                summary.venueName(), TIME.format(start), summary.permissionTone(), false, linkable);
        return new MonthGrid.Booking(start.toLocalDate(), lastDay, entry);
    }

    /** The signed-in coordinator's club's requests, with the actions they may take. */
    public List<RequestSummary> forClubOf(long userId) {
        AppUser user = findUser(userId);
        if (user.getClub() == null) {
            return List.of();
        }
        Instant now = clock.instant();
        return requests.findByClubIdOrderByStartsAtDesc(user.getClub().getId()).stream()
                .map(request -> summarize(request, user, now))
                .toList();
    }

    /**
     * The signed-in reviewer's queue. Venue office: requests waiting for a
     * hall decision. Dean: requests waiting for permission, plus a read-only
     * list of requests stuck at the venue office. Backup approver: every
     * escalated request, at either stage.
     */
    public ReviewQueue queueFor(long userId) {
        AppUser reviewer = findUser(userId);
        Instant now = clock.instant();
        List<EventRequest> pending = requests.findByStatusInAndStartsAtAfterOrderByStatusChangedAtAsc(PENDING, now);

        return switch (reviewer.getRole()) {
            case VENUE_OFFICE -> new ReviewQueue(
                    "Hall requests",
                    "Confirm or reject the venue. Confirmed requests go to the dean for permission.",
                    summaries(pending, RequestStatus.PENDING_VENUE, reviewer, now),
                    null, List.of());
            case DEAN -> new ReviewQueue(
                    "Permission requests",
                    "The hall is already confirmed for these. Your approval is the final permission.",
                    summaries(pending, RequestStatus.PENDING_DEAN, reviewer, now),
                    "Stuck at the hall reservation office",
                    pending.stream()
                            .filter(r -> r.getStatus() == RequestStatus.PENDING_VENUE)
                            .filter(r -> r.isOverdue(now) || r.isEscalated(now))
                            .map(r -> summarize(r, reviewer, now))
                            .toList());
            case BACKUP_APPROVER -> new ReviewQueue(
                    "Escalated requests",
                    "Requests still pending within " + EventRequest.ESCALATION_WINDOW.toDays()
                            + " days of the event. You may decide them in place of the regular reviewer.",
                    pending.stream()
                            .filter(r -> r.isEscalated(now))
                            .map(r -> summarize(r, reviewer, now))
                            .toList(),
                    null, List.of());
            case CLUB -> throw new NotPermittedException("Club coordinators don't have a review queue");
        };
    }

    /**
     * One request with its full history. Reviewers may open any request; a
     * club coordinator may open only their own club's.
     */
    public RequestDetail detail(long requestId, long viewerId) {
        AppUser viewer = findUser(viewerId);
        EventRequest request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new NotFoundException("Event request " + requestId + " was not found"));
        if (viewer.getRole() == Role.CLUB && !request.isOwnedBy(viewer)) {
            throw new NotPermittedException("You can only open your own club's requests");
        }
        Instant now = clock.instant();
        List<HistoryEntry> history = request.getHistory().stream()
                .map(action -> new HistoryEntry(
                        STAMP.format(action.getCreatedAt().atZone(zone)),
                        action.getActor() == null ? "System" : action.getActor().getDisplayName(),
                        actionLabel(action.getAction()),
                        action.getFromStatus() == null ? null : action.getFromStatus().label(),
                        action.getToStatus().label(),
                        action.getRemark()))
                .toList();
        return new RequestDetail(
                summarize(request, viewer, now),
                request.getDescription(),
                request.getSubmittedBy().getDisplayName(),
                STAMP.format(request.getCreatedAt().atZone(zone)),
                history);
    }

    /**
     * The first full day a club may pick, given the 30-day notice rule. Used
     * as the date picker's minimum and in the form's hint.
     */
    public LocalDate earliestEventDate() {
        return ZonedDateTime.ofInstant(clock.instant().plus(EventRequest.MIN_NOTICE), zone).toLocalDate().plusDays(1);
    }

    private List<RequestSummary> summaries(List<EventRequest> pending, RequestStatus stage,
                                           AppUser viewer, Instant now) {
        return pending.stream()
                .filter(r -> r.getStatus() == stage)
                .map(r -> summarize(r, viewer, now))
                .sorted(URGENCY)
                .toList();
    }

    private AppUser findUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " was not found"));
    }

    private static String actionLabel(ActionType action) {
        return switch (action) {
            case SUBMITTED -> "Submitted";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case CANCELLED -> "Cancelled";
            case REMINDED -> "Sent a reminder";
            case EXPIRED -> "Expired";
        };
    }

    /** "3 days", "5 hours", "just now". */
    private static String waitingLabel(Duration waited) {
        long days = waited.toDays();
        if (days >= 1) {
            return days == 1 ? "1 day" : days + " days";
        }
        long hours = waited.toHours();
        if (hours >= 1) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return "just now";
    }

    private RequestSummary summarize(EventRequest request, AppUser viewer, Instant now) {
        ZonedDateTime start = request.getStartsAt().atZone(zone);
        ZonedDateTime end = request.getEndsAt().atZone(zone);
        boolean extended = !OperatingHours.within(request.getStartsAt(), request.getEndsAt(), zone)
                || Duration.between(request.getStartsAt(), request.getEndsAt())
                .compareTo(EventRequest.MAX_DURATION) > 0;
        String timeLabel = start.toLocalDate().equals(end.toLocalDate())
                ? TIME.format(start) + " – " + TIME.format(end)
                : TIME.format(start) + " → " + END_DAY.format(end) + " " + TIME.format(end);
        HallStatus hall = request.hallStatus(now);
        PermissionStatus permission = request.permissionStatus(now);
        boolean owner = viewer != null && request.isOwnedBy(viewer);

        return new RequestSummary(
                request.getId(),
                request.getTitle(),
                request.getClub().getName(),
                request.getVenue().getName(),
                request.getVenue().getCapacity(),
                request.eventTypeLabel(),
                request.getExpectedAttendance(),
                DATE.format(start),
                timeLabel,
                request.isExpired(now) ? "Expired" : request.getStatus().label(),
                hall.label(),
                toneOf(hall),
                permission.label(),
                toneOf(permission),
                request.isOverdue(now),
                request.isEscalated(now),
                request.exceedsVenueCapacity(),
                extended,
                request.getDepartmentApprovalRef(),
                owner && request.getStatus().holdsVenue() && request.getStartsAt().isAfter(now),
                owner && request.canRemind(viewer, now),
                viewer != null && request.canBeReviewedBy(viewer, now),
                waitingLabel(request.timeInCurrentStatus(now)),
                request.getRejectionReason(),
                request.getVenue().getId(),
                YearMonth.from(start).toString());
    }

    private static String toneOf(HallStatus hall) {
        return switch (hall) {
            case TENTATIVE -> "warn";
            case CONFIRMED -> "info";
            case RELEASED -> "muted";
        };
    }

    private static String toneOf(PermissionStatus permission) {
        return switch (permission) {
            case PENDING -> "warn";
            case GRANTED -> "ok";
            case DENIED, EXPIRED -> "bad";
            case WITHDRAWN -> "muted";
        };
    }
}