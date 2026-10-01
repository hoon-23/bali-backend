-- 알림함(2026-10-01): notification_log를 서버가 보낸 푸시 목록으로 재사용한다. 기존 행은 title/body가 빈 문자열이다.
ALTER TABLE notification_log
    ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN body VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX idx_notification_log_user_sent ON notification_log (user_id, sent_at DESC);
CREATE INDEX idx_notification_log_user_unread ON notification_log (user_id) WHERE read_at IS NULL;
