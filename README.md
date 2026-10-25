# EventDesk

Venue booking and event approval for college clubs, in one place instead of an email chain.

**Live:** https://eventdesk.onrender.com/ — free tier, so the first load after a quiet spell can take about a minute while the server wakes up.

## The problem

At VIT-AP, getting a club event approved happens over email. The club writes to the hall reservation office to ask for a venue, waits for a reply, then writes to the Dean of Clubs & Chapters for permission. Clubs can't see each other's bookings, so two clubs can ask for the same hall and find out days later. Nobody can tell at a glance where a request is stuck, and a confirmed hall is easily mistaken for an approved event.

## Who it's for

- **Club coordinators**, who submit events and need to know where each request stands.
- **The hall reservation office**, which confirms or turns down venues.
- **The Dean of Clubs & Chapters**, who gives final permission.

## What it does

- **Shared venue calendar**: a month view of every club's bookings, filterable by venue, so clubs see what's free before they ask. A club clicks **+** on a day to start a request for that date; days inside the 30-day notice period are greyed out.
- **One request form** for the venue and the event details, with the college's rules built in: at least 30 days' notice, 07:00–22:00, at most 12 hours. An attendance estimate is required for technical events and workshops.
- **Two approvals in order**: the hall reservation office confirms the venue first, then the Dean grants permission. A rejection always carries a reason.
- **Hall and permission shown separately**: "Hall confirmed · Permission pending" never reads as approved. Only the Dean's approval authorizes an event.
- **No double-booking**: a pending request holds its slot, and the database itself refuses overlapping bookings for the same venue.
- **Stuck requests surface**: a request waiting more than 2 days at one stage is marked overdue, and the club can send a reminder (at most once a day). Within 7 days of the event it is escalated, and a backup approver can decide in place of the silent reviewer.
- **Expiry**: a request still undecided when its event starts becomes Expired. It is never approved, and the hall is released.
- **Extended technical events**: 24-hour hackathons and other events over 12 hours or overnight are allowed for technical events when the club records the department's approval reference. Reviewers see it flagged.
- **Full history** on every request: who did what, and when.

## Try it

Open the live link and use one of the demo accounts on the sign-in page. One click signs you in. Every account's password is `demo1234`.

| Account | Role |
|---|---|
| `coding@example.org` (and other club accounts) | Club coordinator |
| `venue.office@example.org` | Hall reservation office |
| `dean.clubs@example.org` | Dean of Clubs & Chapters |
| `backup.approver@example.org` | Backup approver (escalated requests only) |

A quick walk-through:
1. As Coding Club, submit a request at least 30 days out.
2. As the hall reservation office, approve it.
3. As the Dean, approve it.
4. Back as the club, the request shows "Permission granted".

## Tech stack

Java 21 · Spring Boot 4 (Web MVC, Security, Data JPA, Validation) · Thymeleaf · PostgreSQL · Flyway · JUnit 5 · Docker · Render (app) and Neon (database).

## Run it locally

Requirements: JDK 21 or newer, and PostgreSQL 14 or newer.

1. Create a database:
   ```sql
   CREATE DATABASE eventdesk;
   ```
2. Set the database password as an environment variable, never in a file:
   ```powershell
   # Windows (PowerShell, current session)
   $env:DATABASE_PASSWORD = "your-postgres-password"
   ```
   ```bash
   # macOS / Linux
   export DATABASE_PASSWORD=your-postgres-password
   ```
   By default the app connects to `jdbc:postgresql://localhost:5432/eventdesk` as `postgres`. Override these with `DATABASE_URL` and `DATABASE_USERNAME`.
3. Start the app:
   ```bash
   ./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
   ```
   Flyway creates the tables and the demo data on first start.
4. Open http://localhost:8080.

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/eventdesk` | JDBC URL |
| `DATABASE_USERNAME` | `postgres` | Database user |
| `DATABASE_PASSWORD` | *(empty)* | Database password |
| `DEMO_LOGINS_ENABLED` | `true` | Show the one-click demo accounts on the sign-in page |
| `PORT` | `8080` | HTTP port (Render sets this) |
| `DB_POOL_SIZE` | `5` | Max database connections |

### Tests

```bash
./mvnw test -Dtest="com.eventdesk.**.*Test"
```

These are unit tests for the approval rules (state machine, deadlines, escalation, expiry, extended events) and the calendar layout. They need no database. `EventdeskApplicationTests` also starts the full app against PostgreSQL in Docker via Testcontainers, so it needs Docker running.

## Deploying

The `Dockerfile` builds the jar and runs it on a JRE image. On Render, create a Web Service from this repo with the Docker runtime, then set `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` to a PostgreSQL database (Neon's free tier works). Migrations run automatically on start.

## Design notes

- **Rules live in the entity.** `EventRequest` is a small state machine. Every change goes through a method that checks who is acting and when (`approve`, `reject`, `cancel`, `remind`, `expire`). Each is called from one `@Transactional` service method, and every action is written to the audit history in the same transaction.
- **The database guards double-booking.** A PostgreSQL exclusion constraint on (venue, time range) covers pending and approved requests. There is no "is it free?" check in Java, because two clubs submitting at the same moment would both pass it.
- **Simultaneous decisions.** Optimistic locking (`@Version`) makes the second of two clashing decisions fail with a clear message instead of both succeeding.
- **Time is injected** (`Clock`), so tests control "now".
- **Pages read from plain summaries** built in one query per page (`@EntityGraph`), so lists don't fire a query per row.
- **Access control in two layers.** URL rules by role, and the entity re-checks the actor on every action. Clubs can open only their own requests.

## Known limitations

- **No notifications.** Clubs and reviewers find out about changes by opening EventDesk. Email on each status change and a daily overdue digest for reviewers are the next step.
- **One venue per request.** A big event needing two halls takes two requests.
- **Department approval is a reference field**, not its own approval stage.
- **Built for one college.** The halls, roles and rules (30-day notice, 07:00–22:00, a hall office then the Dean) follow VIT-AP's process. Another college would need its own seed data and rule changes, and the limits are constants in code, not settings an admin can change.
- **Demo sign-in only.** There is no sign-up or college single sign-on, and accounts are seeded by a migration.
- **Free-tier hosting.** The server sleeps when idle, so the first request takes about a minute. The expiry job only runs while the server is awake; until it runs, expiry is still computed from the clock, so pages are never wrong.
- **Tests cover the approval rules, not the web layer.** Unit tests cover the request state machine, deadlines, escalation, expiry, extended events and the calendar layout. The controllers and pages have no automated tests, and the one full-app test (`EventdeskApplicationTests`) needs Docker for its PostgreSQL container.
- **Faculty judge invitations** for technical events are not built yet.