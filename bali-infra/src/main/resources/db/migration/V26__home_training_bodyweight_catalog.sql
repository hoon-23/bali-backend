-- 홈트(맨몸 매트 운동) 지원(2026-10-01). 시간으로 버티는 플랭크 계열은 reps 필수 구조와 맞지 않아
-- 카탈로그에서 제거하고, 스크린샷 기준 부위별 맨몸 종목 12종을 추가한다.
-- session_logs/template_items가 참조 중인 행은 FK(RESTRICT)로 CD가 깨지지 않게 남겨두고 수동 정리한다.
DELETE FROM exercises e
WHERE e.scope = 'GLOBAL'
  AND e.variant IS NULL
  AND e.name IN ('플랭크', '사이드플랭크', '할로우홀드')
  AND NOT EXISTS (SELECT 1 FROM session_logs s WHERE s.exercise_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM template_items t WHERE t.exercise_id = e.id);

INSERT INTO exercises (id, name, variant, muscle_group, type, scope, owner_id, equipment) VALUES
(gen_random_uuid(), '버피', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '숄더탭', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '무릎', 'CHEST', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '바이시클크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '리버스크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '레그레이즈', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '파이크푸시업', '무릎', 'SHOULDER', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '벤치딥', NULL, 'TRICEPS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '클로즈', 'TRICEPS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '리버스스노우엔젤', NULL, 'BACK', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '프론Y레이즈', NULL, 'BACK', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT');
