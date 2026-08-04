package com.bali.api.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.util.UUID

// JWT 토큰 프로바이더의 토큰 생성 및 검증 로직을 테스트
class JwtTokenProviderTest {

    // 테스트용 JWT 토큰 프로바이더 인스턴스
    private val provider = JwtTokenProvider(
        secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
        expirationMillis = 3600_000,
    )

    // 토큰 생성 후 검증했을 때 동일한 사용자 ID가 반환되는지 확인
    @Test
    fun `token generated for a user validates back to the same user id`() {
        val userId = UUID.randomUUID()
        val token = provider.generateToken(userId, "test@example.com")

        assertEquals(userId, provider.validateAndGetUserId(token))
    }

    // 유효하지 않은 토큰은 null을 반환하고 예외를 발생시키지 않음
    @Test
    fun `garbage token returns null instead of throwing`() {
        assertNull(provider.validateAndGetUserId("not-a-real-token"))
    }

    // 다른 비밀키로 검증하면 null을 반환
    @Test
    fun `token signed with a different secret returns null`() {
        val userId = UUID.randomUUID()
        val token = provider.generateToken(userId, "test@example.com")

        val otherProvider = JwtTokenProvider(
            secret = "a-completely-different-secret-key-32-bytes!!",
            expirationMillis = 3600_000,
        )

        assertNull(otherProvider.validateAndGetUserId(token))
    }
}
