-- The college tracks attendance only for technical events and workshops.
-- For other event types (cultural, webinar, other) the head count is optional.

ALTER TABLE event_requests ALTER COLUMN expected_attendance DROP NOT NULL;

-- The existing CHECK (expected_attendance > 0) still applies when a value is
-- given; a NULL passes it. This constraint makes the count mandatory where it
-- matters. Keep in sync with EventType.requiresAttendance().
ALTER TABLE event_requests ADD CONSTRAINT attendance_required_for_counted_events
    CHECK (event_type NOT IN ('TECHNICAL', 'WORKSHOP') OR expected_attendance IS NOT NULL);