package com.bali.core.auth

import java.util.UUID

// refresh token 영속성을 위한 포트 인터페이스
interface RefreshTokenRepository {
    // refresh token을 저장하고 저장된 토큰을 반환
    fun save(token: RefreshToken): RefreshToken

    // 토큰 해시로 조회, 없으면 null 반환
    fun findByTokenHash(tokenHash: String): RefreshToken?

    // 지정한 id의 토큰을 폐기 처리 (revoked = true)
    fun revoke(id: UUID)
}
