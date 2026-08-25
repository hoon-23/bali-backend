CREATE TABLE device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    expo_push_token VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_device_tokens_expo_push_token UNIQUE (expo_push_token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);

CREATE TABLE notification_settings (
    user_id UUID PRIMARY KEY,
    routine_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    inactivity_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    summary_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    reference_id UUID,
    expo_ticket_id VARCHAR(255),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_error VARCHAR(255),
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_log_type_reference_id ON notification_log(type, reference_id);
CREATE INDEX idx_notification_log_user_id_type_sent_at ON notification_log(user_id, type, sent_at);
