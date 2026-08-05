CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    variant VARCHAR(255),
    muscle_group VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    scope VARCHAR(255) NOT NULL,
    owner_id UUID
);

CREATE INDEX idx_exercises_name_trgm ON exercises USING GIN (name gin_trgm_ops);
