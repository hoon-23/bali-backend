-- 기존 row는 '예정된 운동' 개념이 생기기 전에 만들어진 세션이므로 이미 지나간 기록으로 보고
-- COMPLETED로 백필한다. DEFAULT는 백필 목적으로만 쓰고, 이후 신규 세션은 애플리케이션이 항상
-- 명시적으로 status를 지정하므로 바로 제거한다 (V9->V10의 nickname DEFAULT 제거와 동일 패턴).
ALTER TABLE sessions ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE sessions ALTER COLUMN status DROP DEFAULT;
