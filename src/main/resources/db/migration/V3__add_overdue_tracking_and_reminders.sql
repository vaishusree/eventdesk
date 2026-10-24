-- Overdue tracking and club reminders.
--
-- status_changed_at: when the request entered its current status. "Overdue"
-- is measured from here, so a request that just reached the dean isn't
-- counted as late because the venue office was slow.
-- last_reminder_at: when the club last nudged the reviewer (rate limiting).

ALTER TABLE event_requests
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN last_reminder_at  TIMESTAMPTZ;

-- Backfill from the audit trail: the latest action is the last status change,
-- because no REMINDED actions exist before this migration.
UPDATE event_requests e
SET status_changed_at = COALESCE(
        (SELECT max(a.created_at) FROM request_actions a WHERE a.event_request_id = e.id),
        e.updated_at);

ALTER TABLE event_requests ALTER COLUMN status_changed_at SET NOT NULL;

-- Find overdue requests quickly: pending ones, oldest first.
CREATE INDEX idx_event_requests_pending_age ON event_requests (status, status_changed_at)
    WHERE status IN ('PENDING_VENUE', 'PENDING_DEAN');

-- Reminders are recorded in the audit trail as a new action type.
ALTER TABLE request_actions DROP CONSTRAINT request_actions_action_check;
ALTER TABLE request_actions ADD CONSTRAINT request_actions_action_check
    CHECK (action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'REMINDED'));
