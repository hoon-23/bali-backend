CREATE TABLE weekly_analyses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    week_of DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    summary JSONB
);

CREATE TABLE insights (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES weekly_analyses(id) ON DELETE CASCADE,
    summary_text VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX idx_weekly_analyses_user_id_week_of ON weekly_analyses(user_id, week_of);
CREATE INDEX idx_insights_analysis_id ON insights(analysis_id);
