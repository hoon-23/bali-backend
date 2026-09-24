-- bali-frontend 요청(2026-09-24): 매트 코어 안정화 운동은 ABS로 욱여넣으면 근육군별
-- 집중도 통계가 부정확해져서 별도 근육군(FUNCTIONAL)으로 분리, 초기 GLOBAL 종목 시딩.
INSERT INTO exercises (id, name, variant, muscle_group, type, scope, owner_id, equipment) VALUES
(gen_random_uuid(), '플랭크', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '사이드플랭크', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '데드버그', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '버드독', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '할로우홀드', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '슈퍼맨', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '마운틴클라이머', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT');
