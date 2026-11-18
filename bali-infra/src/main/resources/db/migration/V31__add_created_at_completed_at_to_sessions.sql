-- 같은 date 안의 목록 정렬을 완료/생성 시각으로 하기 위한 컬럼. 기존 행의 created_at은 date 자정(UTC)으로 채워 종전 순서(id)와 충돌하지 않게 한다
ALTER TABLE sessions ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE sessions ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE sessions SET created_at = (date::timestamp AT TIME ZONE 'UTC');

-- 기존 COMPLETED 세션은 세트 기록(set_timings)의 마지막 endedAt이 있으면 그것을 완료 시각으로 복원한다(없으면 NULL로 두고 created_at으로 정렬)
UPDATE sessions s SET completed_at = t.last_end
FROM (
    SELECT l.session_id, max((e->>'endedAt')::timestamptz) AS last_end
    FROM session_logs l, jsonb_array_elements(l.set_timings) e
    WHERE l.set_timings IS NOT NULL AND jsonb_typeof(l.set_timings) = 'array'
    GROUP BY l.session_id
) t
WHERE s.id = t.session_id AND s.status = 'COMPLETED' AND t.last_end IS NOT NULL;

ALTER TABLE sessions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE sessions ALTER COLUMN created_at SET DEFAULT now();

-- 목록 조회(user_id, date DESC, 정렬 시각 DESC, id DESC) 전용 인덱스. 기존 (user_id, date) 인덱스는 이 인덱스의 앞부분과 같아 대체한다
CREATE INDEX idx_sessions_user_date_sort ON sessions (user_id, date DESC, (COALESCE(completed_at, created_at)) DESC, id DESC);
DROP INDEX idx_sessions_user_id_date;
