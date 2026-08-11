ALTER TABLE users DROP CONSTRAINT chk_users_provider;
ALTER TABLE users ADD CONSTRAINT chk_users_provider
    CHECK (provider IN ('GOOGLE', 'NAVER', 'KAKAO', 'APPLE'));
