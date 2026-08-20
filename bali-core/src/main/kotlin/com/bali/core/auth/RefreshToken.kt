package com.bali.core.auth

import java.time.Instant
import java.util.UUID

// 발급된 refresh token 1건. 원본 토큰 값은 저장하지 않고 해시만 보관한다
data class RefreshToken(
    val id: UUID?,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean = false,
    val createdAt: Instant = Instant.now(),
) {
    // 만료되지 않았고 폐기되지 않았으면 유효
    fun isValid(now: Instant): Boolean = !revoked && expiresAt > now
}
