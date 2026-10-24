-- Demo data so reviewers can try every role without signing up.
-- All demo accounts share the password "demo1234" (BCrypt hash below).
-- These are public demo credentials for a demo deployment, not secrets.
-- Event times are relative to when this migration runs, so the calendar
-- is never empty on a fresh deploy. They are 33-36 days out because clubs
-- must request at least 30 days ahead (EventRequest.MIN_NOTICE).

INSERT INTO clubs (name, email) VALUES
    ('Coding Club',           'codingclub@example.org'),
    ('Robotics Club',         'robotics@example.org'),
    ('Entrepreneurship Club', 'ecell@example.org'),
    ('Music Club',            'music@example.org'),
    ('IEEE Student Branch',   'ieee@example.org');

INSERT INTO venues (name, location, capacity) VALUES
    ('Main Auditorium',  'Central Block',     800),
    ('Seminar Hall A',   'Academic Block 1',  150),
    ('Seminar Hall B',   'Academic Block 2',  120),
    ('Conference Room',  'Admin Block',        40),
    ('Open Air Theatre', 'Near Hostel Blocks', 1000);

INSERT INTO app_users (email, password_hash, display_name, role, club_id)
SELECT u.email, '$2a$10$6axdsIlEvcmu/t7NwUgzBO6BR1qt5CQrMBVadlrxdtUxteEUWZ/0m', u.display_name, u.role,
       (SELECT id FROM clubs WHERE name = u.club_name)
FROM (VALUES
    ('coding@example.org',       'Coding Club Coordinator',           'CLUB',         'Coding Club'),
    ('robotics@example.org',     'Robotics Club Coordinator',         'CLUB',         'Robotics Club'),
    ('ecell@example.org',        'Entrepreneurship Club Coordinator', 'CLUB',         'Entrepreneurship Club'),
    ('music@example.org',        'Music Club Coordinator',            'CLUB',         'Music Club'),
    ('ieee@example.org',         'IEEE Branch Coordinator',           'CLUB',         'IEEE Student Branch'),
    ('venue.office@example.org', 'Hall Reservation Office',           'VENUE_OFFICE', NULL),
    ('dean.clubs@example.org',   'Dean, Clubs & Chapters',            'DEAN',         NULL)
) AS u (email, display_name, role, club_name);

-- Sample requests in every interesting state.
-- day_offset/start_hour/end_hour are in IST (Asia/Kolkata).
INSERT INTO event_requests (club_id, venue_id, submitted_by, title, description, event_type,
                            expected_attendance, starts_at, ends_at, status, rejection_reason,
                            created_at, updated_at)
SELECT c.id, v.id, u.id, r.title, r.description, r.event_type, r.attendance,
       (date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') + make_interval(days => r.day_offset, hours => r.start_hour)) AT TIME ZONE 'Asia/Kolkata',
       (date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') + make_interval(days => r.day_offset, hours => r.end_hour)) AT TIME ZONE 'Asia/Kolkata',
       r.status, r.rejection_reason, now() - interval '2 days', now() - interval '1 day'
FROM (VALUES
    ('Coding Club', 'Seminar Hall A', 'coding@example.org',
     'Intro to Competitive Programming',
     'Hands-on session on problem solving with C++ and Java for first and second years.',
     'TECHNICAL', 120, 33, 10, 13, 'APPROVED', NULL),
    ('IEEE Student Branch', 'Conference Room', 'ieee@example.org',
     'Webinar: Careers in VLSI',
     'Online talk by an industry speaker, streamed to students in the conference room.',
     'WEBINAR', 35, 33, 15, 17, 'APPROVED', NULL),
    ('Robotics Club', 'Seminar Hall B', 'robotics@example.org',
     'Line Follower Robot Workshop',
     'Build and race a line-following robot. Kits provided by the club.',
     'WORKSHOP', 60, 34, 14, 17, 'PENDING_DEAN', NULL),
    ('Entrepreneurship Club', 'Main Auditorium', 'ecell@example.org',
     'Founder Fireside Chat',
     'Conversation with alumni founders about starting up straight out of college.',
     'OTHER', 400, 35, 16, 18, 'PENDING_VENUE', NULL),
    ('Music Club', 'Open Air Theatre', 'music@example.org',
     'Open Mic Night',
     'Evening of student performances: music, poetry and stand-up.',
     'CULTURAL', 500, 36, 18, 21, 'REJECTED', 'The Open Air Theatre is under maintenance that week.')
) AS r (club_name, venue_name, submitter_email, title, description, event_type, attendance,
        day_offset, start_hour, end_hour, status, rejection_reason)
JOIN clubs c ON c.name = r.club_name
JOIN venues v ON v.name = r.venue_name
JOIN app_users u ON u.email = r.submitter_email;

-- Audit trail for the sample requests, consistent with their current status.
INSERT INTO request_actions (event_request_id, actor_id, action, from_status, to_status, remark, created_at)
SELECT e.id, u.id, a.action, a.from_status, a.to_status, a.remark, now() - a.ago
FROM (VALUES
    ('Intro to Competitive Programming', 'coding@example.org',       'SUBMITTED', NULL,            'PENDING_VENUE', NULL, interval '2 days'),
    ('Intro to Competitive Programming', 'venue.office@example.org', 'APPROVED',  'PENDING_VENUE', 'PENDING_DEAN',  NULL, interval '36 hours'),
    ('Intro to Competitive Programming', 'dean.clubs@example.org',   'APPROVED',  'PENDING_DEAN',  'APPROVED',      NULL, interval '1 day'),
    ('Webinar: Careers in VLSI',         'ieee@example.org',         'SUBMITTED', NULL,            'PENDING_VENUE', NULL, interval '2 days'),
    ('Webinar: Careers in VLSI',         'venue.office@example.org', 'APPROVED',  'PENDING_VENUE', 'PENDING_DEAN',  NULL, interval '36 hours'),
    ('Webinar: Careers in VLSI',         'dean.clubs@example.org',   'APPROVED',  'PENDING_DEAN',  'APPROVED',      NULL, interval '1 day'),
    ('Line Follower Robot Workshop',     'robotics@example.org',     'SUBMITTED', NULL,            'PENDING_VENUE', NULL, interval '2 days'),
    ('Line Follower Robot Workshop',     'venue.office@example.org', 'APPROVED',  'PENDING_VENUE', 'PENDING_DEAN',  NULL, interval '1 day'),
    ('Founder Fireside Chat',            'ecell@example.org',        'SUBMITTED', NULL,            'PENDING_VENUE', NULL, interval '2 days'),
    ('Open Mic Night',                   'music@example.org',        'SUBMITTED', NULL,            'PENDING_VENUE', NULL, interval '2 days'),
    ('Open Mic Night',                   'venue.office@example.org', 'REJECTED',  'PENDING_VENUE', 'REJECTED',
     'The Open Air Theatre is under maintenance that week.', interval '1 day')
) AS a (title, actor_email, action, from_status, to_status, remark, ago)
JOIN event_requests e ON e.title = a.title
JOIN app_users u ON u.email = a.actor_email;
