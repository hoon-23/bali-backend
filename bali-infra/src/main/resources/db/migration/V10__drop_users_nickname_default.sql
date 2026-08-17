-- V9에서 백필용으로 남겨둔 nickname 컬럼의 DEFAULT ''를 제거한다.
-- 이 기본값이 남아있으면 향후 raw insert(시드 스크립트 등)로 nickname이 빈 문자열인 행이
-- 생길 수 있고, 그런 행은 PATCH로 다른 필드만 바꾸려 해도
-- User.updateProfile의 "nickname must not be blank" 검증에 걸려 수정 자체가 막힌다.
ALTER TABLE users ALTER COLUMN nickname DROP DEFAULT;
