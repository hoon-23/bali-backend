CREATE TABLE templates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE template_items (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL,
    sort_order INT NOT NULL,
    target_sets INT,
    target_reps INT,
    target_weight NUMERIC(6,2),
    target_duration_seconds INT,
    target_pace VARCHAR(255)
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    date DATE NOT NULL,
    template_id UUID REFERENCES templates(id)
);

CREATE TABLE session_logs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL,
    sort_order INT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    target_sets INT,
    target_reps INT,
    target_weight NUMERIC(6,2),
    target_duration_seconds INT,
    target_pace VARCHAR(255),
    actual_sets INT,
    actual_reps INT,
    actual_weight NUMERIC(6,2),
    actual_duration_seconds INT,
    actual_pace VARCHAR(255)
);
