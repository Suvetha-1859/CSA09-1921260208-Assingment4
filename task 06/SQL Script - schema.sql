CREATE DATABASE IF NOT EXISTS campus_events CHARACTER SET utf8mb4;
USE campus_events;

CREATE TABLE IF NOT EXISTS events (
    event_id   INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    event_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS registrations (
    reg_id         INT AUTO_INCREMENT PRIMARY KEY,
    student_reg_no VARCHAR(15)  NOT NULL,
    student_name   VARCHAR(80)  NOT NULL,
    email          VARCHAR(120) NOT NULL,
    event_id       INT          NOT NULL,
    registered_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_event UNIQUE (student_reg_no, event_id),   -- blocks duplicates
    CONSTRAINT fk_reg_event FOREIGN KEY (event_id) REFERENCES events(event_id)
);

INSERT IGNORE INTO events (name, event_date) VALUES
    ('Cultural Night', '2026-12-05'),
    ('Dance Battle',   '2026-12-06'),
    ('Hackathon',      '2026-12-12'),
    ('Quiz Fest',      '2026-12-15');

-- OPTIONAL: 100,000 rows for the paging/responsiveness experiment (MySQL 8)
-- SET SESSION cte_max_recursion_depth = 100000;
-- INSERT INTO registrations (student_reg_no, student_name, email, event_id)
-- WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 100000)
-- SELECT CONCAT('S', LPAD(i, 7, '0')), CONCAT('Student ', i), CONCAT('s', i, '@campus.edu'), 1 + MOD(i, 4) FROM n;
