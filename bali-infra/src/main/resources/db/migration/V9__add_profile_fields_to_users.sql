-- User 프로필 확장: 닉네임(표시명) + 주간 목표 운동 횟수 컬럼 추가.
-- 애플리케이션 신규 가입 로직(SocialLoginService)이 NicknameGenerator로 항상 실제 닉네임을
-- 채우므로, DEFAULT ''는 기존 행 백필을 위한 임시값일 뿐이다. 즉시 UPDATE로 정리한다.
-- (이 프로젝트는 아직 실사용자가 없는 개발 단계라 Flyway Java 콜백 같은 정교한 백필 로직은
-- 오버엔지니어링으로 판단 — docs/specs/2026-08-17-user-profile-expansion-design.md 참고)
ALTER TABLE users ADD COLUMN nickname VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN weekly_goal_sessions INT NOT NULL DEFAULT 3;

UPDATE users SET nickname = '사용자' || substr(id::text, 1, 4) WHERE nickname = '';
