-- Apple 로그인 사용자의 Apple refresh token. 탈퇴 시 Apple 연동을 revoke(Guideline 5.1.1(v))하는 데만 쓴다
CREATE TABLE apple_refresh_tokens (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    refresh_token TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
