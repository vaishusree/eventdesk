-- Technical events may run longer than 12 hours or outside 07:00-22:00
-- (e.g. 24-hour hackathons) if the club records the department's approval.
-- The rule itself lives in EventRequest / OperatingHours; this column stores
-- the reference (e.g. an HOD approval letter number) so reviewers can see it.
ALTER TABLE event_requests ADD COLUMN department_approval_ref VARCHAR(100);

-- Only technical events can carry a department approval reference.
ALTER TABLE event_requests ADD CONSTRAINT approval_ref_only_for_technical
    CHECK (department_approval_ref IS NULL OR event_type = 'TECHNICAL');