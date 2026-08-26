-- 즉흥 추가 등으로 target이 비어있던 과거 세션 로그를, 실제 기록값(actual)으로 1회성 백필한다.
-- COALESCE라 이미 target이 있는 로그는 그대로 유지된다
UPDATE session_logs
SET
    target_sets = COALESCE(target_sets, actual_sets),
    target_reps = COALESCE(target_reps, actual_reps),
    target_weight = COALESCE(target_weight, actual_weight),
    target_duration_seconds = COALESCE(target_duration_seconds, actual_duration_seconds),
    target_pace = COALESCE(target_pace, actual_pace)
WHERE target_sets IS NULL
   OR target_reps IS NULL
   OR target_weight IS NULL
   OR target_duration_seconds IS NULL
   OR target_pace IS NULL;
