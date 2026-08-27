-- 정식 명칭/분류 정리 (id 유지, name/variant/muscle_group만 갱신 — 기존 세션/템플릿 참조 보존)

-- 이름 보정: 수식어만 있던 이름을 정식 명칭으로 완성
UPDATE exercises SET name = '스트레이트암풀다운' WHERE scope = 'GLOBAL' AND name = '암풀다운' AND variant IS NULL;
UPDATE exercises SET name = '벤트오버로우' WHERE scope = 'GLOBAL' AND name = '벤트오버' AND variant IS NULL;

-- 롱풀은 시티드로우(케이블 좌식 로우)의 하위 종목이므로 variant로 흡수
UPDATE exercises SET name = '시티드로우', variant = '롱풀' WHERE scope = 'GLOBAL' AND name = '롱풀' AND variant IS NULL;

-- 외래어 표기법: lateral → 래터럴 (레터럴 표기 통일)
UPDATE exercises SET name = '사이드래터럴레이즈' WHERE scope = 'GLOBAL' AND name = '사이드레터럴레이즈';
UPDATE exercises SET name = '래터럴로우' WHERE scope = 'GLOBAL' AND name = '레터럴로우' AND variant IS NULL;

-- 펙덱플라이(기본형)는 가슴 운동. 리버스 펙덱(후면삼각근)만 SHOULDER로 남긴다
UPDATE exercises SET muscle_group = 'CHEST' WHERE scope = 'GLOBAL' AND name = '펙덱플라이' AND variant IS NULL;

-- equipment 백필 (프리웨이트/머신/케이블/스미스/맨몸). 유산소 종목은 대상 아님(계속 NULL)
UPDATE exercises e SET equipment = 'FREE_WEIGHT'
FROM (VALUES
    ('랙풀', NULL::varchar), ('바벨로우', NULL::varchar), ('덤벨로우', NULL::varchar), ('덤벨로우', '원암'), ('벤트오버로우', NULL::varchar),
    ('바벨컬', NULL::varchar), ('덤벨컬', NULL::varchar), ('해머컬', NULL::varchar), ('리버스컬', NULL::varchar), ('이지바컬', NULL::varchar),
    ('벤치프레스', NULL::varchar), ('벤치프레스', '인클라인'), ('벤치프레스', '디클라인'), ('덤벨프레스', NULL::varchar), ('덤벨프레스', '인클라인'), ('체스트플라이', NULL::varchar),
    ('시티드스컬크러셔', NULL::varchar), ('오버헤드익스텐션', NULL::varchar), ('라잉트라이셉스익스텐션', NULL::varchar), ('트라이셉스익스텐션', NULL::varchar),
    ('숄더프레스', NULL::varchar), ('밀리터리프레스', NULL::varchar), ('밀리터리프레스', '시티드'), ('OHP', NULL::varchar), ('OHP', '시티드'),
    ('비하인드넥프레스', NULL::varchar), ('덤벨숄더프레스', NULL::varchar), ('사이드래터럴레이즈', NULL::varchar), ('리어래터럴레이즈', NULL::varchar), ('프론트레이즈', NULL::varchar), ('업라이트로우', NULL::varchar),
    ('스쿼트', NULL::varchar), ('불가리안스플릿스쿼트', NULL::varchar), ('런지', NULL::varchar), ('데드리프트', NULL::varchar)
) AS v(name, variant)
WHERE e.scope = 'GLOBAL' AND e.type = 'STRENGTH' AND e.name = v.name AND e.variant IS NOT DISTINCT FROM v.variant;

UPDATE exercises e SET equipment = 'MACHINE'
FROM (VALUES
    ('바벨로우', '머신'), ('티바로우', NULL::varchar), ('머신로우', NULL::varchar), ('로우로우', NULL::varchar), ('래터럴로우', NULL::varchar),
    ('머신컬', NULL::varchar),
    ('머신체스트프레스', NULL::varchar), ('머신체스트프레스', '인클라인'), ('머신체스트프레스', '디클라인'), ('체스트플라이', '머신'), ('체스트플라이', '버터플라이'), ('펙덱플라이', NULL::varchar),
    ('숄더프레스', '머신'), ('사이드래터럴레이즈', '머신'), ('펙덱플라이', '리버스'), ('힙허거', NULL::varchar),
    ('브이스쿼트', NULL::varchar), ('핵스쿼트', NULL::varchar), ('레그프레스', NULL::varchar), ('레그프레스', '원레그'), ('레그컬', NULL::varchar), ('레그익스텐션', NULL::varchar), ('런지', '머신')
) AS v(name, variant)
WHERE e.scope = 'GLOBAL' AND e.type = 'STRENGTH' AND e.name = v.name AND e.variant IS NOT DISTINCT FROM v.variant;

UPDATE exercises e SET equipment = 'CABLE'
FROM (VALUES
    ('랫풀다운', NULL::varchar), ('랫풀다운', '오버그립'), ('랫풀다운', '언더그립'), ('랫풀다운', '클로즈그립'), ('랫풀다운', '와이드그립'), ('랫풀다운', '해머그립'), ('랫풀다운', '프론트'),
    ('시티드로우', NULL::varchar), ('시티드로우', '와이드'), ('시티드로우', '클로즈'), ('시티드로우', '롱풀'), ('하이로우', NULL::varchar), ('케이블로우', NULL::varchar), ('케이블로우', '원암'), ('스트레이트암풀다운', NULL::varchar),
    ('케이블컬', NULL::varchar),
    ('체스트플라이', '케이블'),
    ('케이블푸시다운', NULL::varchar), ('케이블익스텐션', NULL::varchar), ('케이블킥백', NULL::varchar), ('케이블킥백', '원암'),
    ('페이스풀', NULL::varchar), ('케이블리어델트플라이', NULL::varchar),
    ('케이블크런치', NULL::varchar)
) AS v(name, variant)
WHERE e.scope = 'GLOBAL' AND e.type = 'STRENGTH' AND e.name = v.name AND e.variant IS NOT DISTINCT FROM v.variant;

UPDATE exercises e SET equipment = 'SMITH'
FROM (VALUES
    ('벤치프레스', '스미스'), ('숄더프레스', '스미스')
) AS v(name, variant)
WHERE e.scope = 'GLOBAL' AND e.type = 'STRENGTH' AND e.name = v.name AND e.variant IS NOT DISTINCT FROM v.variant;

UPDATE exercises e SET equipment = 'BODYWEIGHT'
FROM (VALUES
    ('풀업', NULL::varchar), ('딥스', NULL::varchar), ('스쿼트', '원레그'), ('행잉레그레이즈', NULL::varchar)
) AS v(name, variant)
WHERE e.scope = 'GLOBAL' AND e.type = 'STRENGTH' AND e.name = v.name AND e.variant IS NOT DISTINCT FROM v.variant;
