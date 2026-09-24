-- 애플워치 "기능성 근력운동" 카테고리 벤치마킹 중 매트 맨몸 푸시업 계열이 카탈로그에
-- 없다는 걸 확인해서 추가한다(2026-09-24). 파이크푸시업은 코어보다 어깨 프레스 패턴이라
-- FUNCTIONAL이 아니라 기존 근육군(CHEST/SHOULDER)에 맨몸 variant로 넣는다.
INSERT INTO exercises (id, name, variant, muscle_group, type, scope, owner_id, equipment) VALUES
(gen_random_uuid(), '푸시업', NULL, 'CHEST', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '다이아몬드', 'CHEST', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '와이드', 'CHEST', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '파이크푸시업', NULL, 'SHOULDER', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT');
