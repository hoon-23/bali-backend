CREATE TABLE monthly_analyses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    month_of DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    summary JSONB
);

CREATE TABLE monthly_insights (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES monthly_analyses(id) ON DELETE CASCADE,
    summary_text VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX idx_monthly_analyses_user_id_month_of ON monthly_analyses(user_id, month_of);
CREATE INDEX idx_monthly_insights_analysis_id ON monthly_insights(analysis_id);
