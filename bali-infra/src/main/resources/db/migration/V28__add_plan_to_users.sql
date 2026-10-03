-- 무료/유료 플랜(2026-10-03). 구독은 plan_expires_at에 만료 시각을 넣고, 평생 이용권은 NULL로 둔다.
ALTER TABLE users
    ADD COLUMN plan VARCHAR(10) NOT NULL DEFAULT 'FREE',
    ADD COLUMN plan_expires_at TIMESTAMPTZ;

-- 직접 만든 운동 개수 조회(한도 판정)용. PERSONAL 행만 인덱싱한다
CREATE INDEX idx_exercises_owner_id_personal ON exercises(owner_id) WHERE scope = 'PERSONAL';
