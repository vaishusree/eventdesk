-- 1. EXPIRED becomes a stored status, so an expired request really releases its
--    slot: no_overlapping_venue_bookings only covers PENDING_VENUE, PENDING_DEAN
--    and APPROVED, so a row moved to EXPIRED no longer blocks the venue.
ALTER TABLE event_requests DROP CONSTRAINT event_requests_status_check;
ALTER TABLE event_requests ADD CONSTRAINT event_requests_status_check
    CHECK (status IN ('PENDING_VENUE', 'PENDING_DEAN', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

-- Lets the expiry sweep find pending requests whose event has started.
CREATE INDEX idx_event_requests_pending_start ON event_requests (starts_at)
    WHERE status IN ('PENDING_VENUE', 'PENDING_DEAN');

-- 2. Expiry is recorded in the audit trail as a system action with no person
--    as actor. Every other action must name the person who did it.
ALTER TABLE request_actions DROP CONSTRAINT request_actions_action_check;
ALTER TABLE request_actions ADD CONSTRAINT request_actions_action_check
    CHECK (action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'REMINDED', 'EXPIRED'));
ALTER TABLE request_actions ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE request_actions ADD CONSTRAINT actor_required_unless_system
    CHECK ((action = 'EXPIRED') = (actor_id IS NULL));

-- 3. Backup approver: decides escalated requests when the regular reviewer
--    hasn't responded. Rename once the college confirms the real office.
ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('CLUB', 'VENUE_OFFICE', 'DEAN', 'BACKUP_APPROVER'));

-- Demo account, same demo password as the others ("demo1234").
INSERT INTO app_users (email, password_hash, display_name, role, club_id)
VALUES ('backup.approver@example.org',
        '$2a$10$6axdsIlEvcmu/t7NwUgzBO6BR1qt5CQrMBVadlrxdtUxteEUWZ/0m',
        'Backup Approver, Clubs & Chapters', 'BACKUP_APPROVER', NULL);