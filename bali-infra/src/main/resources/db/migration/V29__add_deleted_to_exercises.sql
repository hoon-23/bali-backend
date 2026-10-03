-- 개인 종목 삭제를 소프트 삭제로 전환(2026-10-03). 기록(session_logs/template_items)이 참조하는 종목도
-- 사용자 목록에서만 숨기고 기록은 보존한다.
ALTER TABLE exercises ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;
