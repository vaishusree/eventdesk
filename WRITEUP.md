# EventDesk: club event approval without the email chain

**Problem and user.** At VIT-AP, a club coordinator planning an event emails the hall reservation office for a venue, waits, then emails the Dean of Clubs & Chapters for permission. Clubs can't see each other's bookings, so two can chase the same hall, and nobody can see where a request is stuck. EventDesk is for those three: club coordinators, the hall office and the Dean.

**Key decisions**

1. **One request, two approvals in order.** The hall office confirms the venue first, and only then does the request reach the Dean. Hall status and permission are shown separately, because "hall confirmed" is easy to mistake for "approved". Only the Dean's approval authorizes an event.
2. **Pending requests hold the slot, enforced by the database.** Otherwise two clubs could wait days for one slot, and one would lose. A PostgreSQL exclusion constraint on venue and time range rejects overlaps. A "free?" check in Java could let two simultaneous submissions through.
3. **A policy for silence.** I asked: the event is at 10:00, the hall is confirmed, the Dean hasn't replied. What happens? My overdue flag only changed a colour. Now a request is overdue after 2 days at a stage (the club can send a reminder), escalated within 7 days of the event (a backup approver can decide), and expired if nobody decides before it starts. An expired request is never approved, and its hall is released.

**Cut, and next steps.** Notifications: a club only sees a change by opening EventDesk. Email on each status change and a daily digest of overdue requests come next. Also cut: multi-hall requests, letting the hall office block halls for exams, a department approval stage, and faculty judge invitations.

**What broke or surprised me**

- The unit tests passed, but every ownership check would have failed against the real database. `Club.sameAs` read the other club's `id` field directly, and Hibernate's lazy proxies leave that field empty. Only `getId()` works.
- Expired requests still blocked their hall: the app said "released", but the row stayed pending, so the overlap constraint kept the slot. Expiry is now a stored status, which frees it.
- My 12-hour limit blocked real 24-hour hackathons. Technical events can now run longer if the club records the department's approval reference.

More detail on design and edge cases: README.md and NOTES.md in the repo.
