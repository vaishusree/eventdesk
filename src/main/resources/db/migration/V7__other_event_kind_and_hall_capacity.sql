-- 1. An "Other" event says what kind of event it is (e.g. "Alumni meet"), so
--    reviewers aren't left guessing. Required for new Other requests (checked
--    in EventRequest); older rows may have none, so the column stays nullable.
ALTER TABLE event_requests ADD COLUMN event_type_detail VARCHAR(60);
ALTER TABLE event_requests ADD CONSTRAINT type_detail_only_for_other
    CHECK (event_type_detail IS NULL OR event_type = 'OTHER');

-- 2. The seminar halls seat more than first seeded.
UPDATE venues SET capacity = 300 WHERE name = 'Seminar Hall A';
UPDATE venues SET capacity = 250 WHERE name = 'Seminar Hall B';