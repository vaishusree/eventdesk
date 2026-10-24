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