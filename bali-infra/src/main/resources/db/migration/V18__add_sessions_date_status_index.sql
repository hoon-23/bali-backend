-- RoutineReminderRunner가 날짜+상태로 전체 유저의 세션을 스캔하는 배치 쿼리를 위한 인덱스
CREATE INDEX idx_sessions_date_status ON sessions(date, status);
