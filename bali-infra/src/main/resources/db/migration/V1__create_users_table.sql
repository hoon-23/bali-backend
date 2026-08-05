-- 주의: Step 1에서 확인한 실제 `users` 테이블 스키마와 정확히 일치해야 한다.
-- UserJpaEntity 필드(id, email, provider, providerId, status, createdAt)를
-- Hibernate 기본 네이밍 전략(camelCase -> snake_case)과 기본 컬럼 길이(255)로 매핑한 것.
-- 실제 psql \d users 출력 확인 결과, id를 제외한 컬럼에는 NOT NULL 제약이 없고
-- created_at은 timestamp(6) with time zone 이므로 아래와 같이 맞춘다.
-- provider/status에는 Hibernate가 @Enumerated(EnumType.STRING) 기준으로 자동 생성했던 CHECK
-- 제약(현재 라이브 스키마 기준)과 동일한 검증 효과를 내도록 의미 있는 이름으로 재작성해 포함한다.
-- AuthProvider(com.bali.core.user.AuthProvider): GOOGLE
-- UserStatus(com.bali.core.user.UserStatus): ACTIVE, WITHDRAWN
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255),
    provider VARCHAR(255),
    provider_id VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT chk_users_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);
