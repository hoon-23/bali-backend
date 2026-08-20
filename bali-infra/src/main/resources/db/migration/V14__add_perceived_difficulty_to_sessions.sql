ALTER TABLE sessions ADD COLUMN perceived_difficulty INT;
ALTER TABLE sessions ADD CONSTRAINT chk_sessions_perceived_difficulty CHECK (perceived_difficulty IS NULL OR perceived_difficulty BETWEEN 1 AND 10);
