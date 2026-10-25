# Dev log

Running notes for the write-up ("what broke or surprised you") and the interview.
Add a line whenever something breaks, surprises you, or you make a decision.

## Fri 23 Oct

- Problem: event approval at VIT-AP runs over email (club -> hall reservation office,
  then club -> Dean of Clubs & Chapters). No club can see another club's bookings.
- Evidence: TODO, ask 3-4 club coordinators how many emails and days one event takes.
- Scope: shared venue calendar + one request form + two-stage approval + status tracking.
  Cut for now: ODs, certificates, faculty mailing.
- Decision: pending requests hold the slot, not just approved ones. Otherwise two
  clubs can wait days for the same hall and one is rejected at the end.
  Trade-off: a club could sit on a slot; the venue office can reject it. Hold
  expiry is a possible next step.
- Decision: double-booking is prevented by a PostgreSQL exclusion constraint
  (btree_gist, tstzrange '[)'), not a check in Java. A "check then insert" in
  Java has a race when two clubs submit at once; the constraint doesn't.
  In HostelFlow capacity was a counter, so a conditional UPDATE worked. Here a
  booking is a time range, so that pattern doesn't fit.
  Verified with two concurrent transactions: the second waits, then fails.
- Decision: half-open ranges, so 10:00-12:00 and 12:00-14:00 don't clash.
- Decision: the approval order is fixed: venue office first, then dean. Rejection
  requires a reason. Every action is recorded in request_actions (audit trail).
- Decision: limits of 12 h per booking and attendance <= venue capacity.
  (Changed later: attendance is an estimate and is only flagged above capacity; see below.)
- Decision: the club can cancel until the event starts, even after approval.
- Decision: demo accounts are seeded by a migration so reviewers don't need to sign up.
- Feature: overdue flag + club reminders, instead of auto-resending emails on a
  timer (people already ignore repeated emails, and reviewers wouldn't see them).
  A pending request is overdue after 2 days *at its current stage*. The clock
  restarts when it moves to the dean, so the dean doesn't inherit the venue
  office's delay. The club can send a reminder once overdue, at most once per
  24 h, and each reminder goes into the audit history. A reminder doesn't reset the clock.
  Next step: a daily email digest of overdue requests to each reviewer.
- Added as a new migration (V3) instead of editing V1: Flyway refuses to run
  if an already-applied migration changes (checksum mismatch).
- Change: the attendance count is an estimate, not a hard limit. It's required for
  technical events and workshops (the college tracks those numbers) and optional
  for cultural events, webinars and other events (new migration V4).
  At first I rejected counts above the hall's capacity. That was wrong: a big
  workshop can need several halls, so going over capacity is now flagged to the
  club and reviewers instead of blocked.
- Cut: one request booking several venues. It needs a booking row per venue and
  the overlap constraint moved onto those rows. For now a club submits one request
  per hall. Next step if there's time.
- Gap found (scenario: hall confirmed for 10:00, dean silent, 9:45 now): my
  "urgent" flag only changed the display; it didn't say what happens if nobody acts.
  Policy now: (1) only final approval authorizes the event; (2) permission status
  is shown separately from hall status ("Hall confirmed, permission NOT granted");
  (3) pending within 7 days of the event = escalated, and the club can remind even
  if the stage is under 2 days old; (4) still pending at start = Expired: out of
  queues, no actions, never approved. Expiry is computed from the clock, not set
  by a scheduled job (changed later: EXPIRED is now stored by a sweep, see (a) below), so a sleeping free-tier server can't leave it wrong.
  Assumption: expiry releases the hall (one constant to flip).
  Open question for the college: who decides when the dean doesn't respond?
- Bug found by the new tests: reject() didn't check the start time, so an
  expired request could be "rejected" afterwards, changing its outcome. Fixed.
- Review feedback round 2, three gaps:
  (a) The DB still held an expired request's slot (status stayed PENDING_*) while
  the domain said "released": two sources of truth. EXPIRED is now a stored
  status (V5) set by a system action (no person as actor, enforced by a DB
  check). The overlap constraint ignores EXPIRED, so the slot is really freed.
  Verified: an overlapping booking is blocked before expiry and allowed after.
  A scheduled sweep will write EXPIRED; isExpired() covers the gap between sweeps.
  (b) Escalation had no recipient. Added a BACKUP_APPROVER role: once a request
  is escalated (within 7 days of the event) it can act in place of whichever
  reviewer is pending. Before escalation it can't. The audit trail records
  who actually decided. The real office is still to be confirmed with the college.
  (c) requireAuthorized(): one guard that any "event can go ahead" feature (ODs,
  certificates, event pass) must call. A confirmed hall alone fails it.
- Small fix found by tests: after the event started, the backup approver got
  "only escalated requests" instead of "event already started". Now the start
  time is checked before the role in approve() and reject().
- Every action that changes a request goes through EventRequestService, one
  @Transactional method per action: load, apply the entity's rules, saveAndFlush.
  Status and the history row are saved together or not at all. @Version on the
  entity makes the second of two simultaneous decisions fail instead of both
  "succeeding". Time comes from an injected Clock so tests can control it.

## Sat 24 Oct: list pages and avoiding N+1 queries

- Before the list pages: no N+1 yet, the only query was findById (one request).
- Risk spots once list pages exist: club/venue per row in calendar and queues,
  exceedsVenueCapacity() (venue), isOwnedBy() (club), and actor per history row
  on the detail page.
- Fix: keep relationships LAZY. Each list query fetches what its page needs in
  one go with @EntityGraph (calendar/queues: club + venue; detail page:
  club, venue, submittedBy, history, history.actor). Don't switch to EAGER:
  it still runs per-row queries and loads data pages don't need.
- Safety net already on: spring.jpa.open-in-view=false, so a missed fetch fails
  loudly (LazyInitializationException) instead of silently running extra queries.
- Not done: a test that loads the calendar and counts SQL statements via Hibernate
  statistics; it must stay at 1 query no matter how many requests exist.
- Bug found before it shipped: Club.sameAs read other.id directly. With a real
  database the user's club is a lazy Hibernate proxy, whose own fields are
  empty; only getId() returns the id. So every ownership check (cancel, remind)
  would have failed with "only the club that submitted...". Unit tests couldn't
  catch it (no proxies there). Fixed to use getId(); ClubTest imitates a proxy.
- Booking clash: no "is the slot free?" query before saving (that would race).
  The insert is attempted and PostgreSQL's exclusion constraint decides; its
  error is translated into a friendly VenueUnavailableException.
- Read side (RequestQueryService, readOnly) builds plain summaries with times
  formatted in IST, so templates never touch lazy relationships.
- Simplification: an event is one date with start/end time, so it can't run past midnight.
  (Changed later: technical events with department approval can run overnight, V6. Every
  other event must still start and end on one day, 07:00-22:00.)
- Review queues: venue office sees PENDING_VENUE, dean sees PENDING_DEAN plus a
  read-only "stuck at the hall office" list, backup approver sees escalated
  requests at either stage. Escalated first, then longest-waiting.
  Buttons appear only via canBeReviewedBy(), which mirrors approve/reject's
  checks; the entity still re-checks on every click.
- Rule: events between 07:00 and 22:00 (OperatingHours). Caught a real slip in
  testing: a webinar entered as 03:00-06:00 instead of 3-6 PM.
- Security detail: approve/reject redirect back via a returnTo field; only our
  own paths are accepted, to avoid an open redirect.
- Gap from how the college works: technical clubs run 24-hour hackathons, which
  the 12-hour / 07:00-22:00 / single-day rules blocked. Now technical events may
  run up to 36 hours or overnight if the club records the department's approval
  reference (V6). Reviewers see them flagged "Extended event" with the reference.
  Cut: a separate department approval stage in the workflow (new role and
  queue); the reference field is the lightweight version.
- Expiry sweep: a scheduled job every 5 minutes stores EXPIRED on requests nobody
  decided before their start (system action, frees the slot in the database).
  If the free-tier server is asleep, isExpired() covers the gap until it runs.

## Setup and deployment

- Bug that only Linux would show: a template folder was named templates/Club, while
  the controllers return "club/requests". Windows ignores letter case in file names,
  so it worked locally; Render runs Linux, where it would have been "template not
  found". Renamed to templates/club.
- Local database name must match the JDBC URL exactly: a database created as
  "EventDesk" is not "eventdesk" to PostgreSQL. Renamed it to lowercase.
- Secrets: the database password is only ever an environment variable
  (DATABASE_PASSWORD), never in application.properties or git. Locally it is set
  in the IntelliJ run configuration; on Render in the service's environment.
  A missing variable fails loudly ("SCRAM-based authentication, but no password
  was provided") instead of silently using a wrong default.
- Hosting: Render free tier for the app (Docker image built from the Dockerfile)
  and Neon free tier for PostgreSQL. Trade-off: Render sleeps after 15 minutes
  idle, so the first visit takes about a minute. Decided against an uptime pinger:
  keeping the app awake would also keep Neon's compute running and use up its
  free monthly hours.
- server.forward-headers-strategy=native, because Render sits behind a proxy;
  without it, redirects such as the login page can point at http instead of https.
- Connection pool capped at 5 (DB_POOL_SIZE): enough for this app, and small
  enough to keep the number of open connections to Neon low.
- Flyway runs every migration on startup, on Neon exactly as locally, so the
  live schema can't drift from the code.

## Sun 25 Oct

- Calendar is now a month grid (MonthGrid, pure Java, unit tested) with a venue filter.
  Clubs click + on a day to start a request for that date; days inside the 30-day
  notice are hatched so it's clear where booking starts.
- Event type "Other" must say what kind of event it is (V7), so reviewers aren't guessing.
- Seminar Hall A seats 300 and B 250 (V7).
- Question from testing: how does the hall office know a hall is free? It doesn't have
  to check: the exclusion constraint refuses clashing requests at submission, so every
  request in its queue was for a free slot. Added a "see this hall on the calendar"
  link for context. Gap: the office can't block a hall for exams or maintenance yet.
- Bug found by an edge-case pass: an event from 20:00 to 00:00 passed the 22:00
  closing rule, because the end time "00:00" compared as earlier than 22:00. Now an
  event must start and end on the same day. Test added.
- Mistake: V6 was committed as an empty file (IntelliJ's "Add to Git" stages the file
  as it is at that moment, and I committed without git add -A). It never reached Neon,
  so nothing broke, but it would have been a checksum mismatch if it had.
- A broken template (duplicate class attribute in fragments.html) made every page
  blank, including the error page. The real error was only in the server log.

