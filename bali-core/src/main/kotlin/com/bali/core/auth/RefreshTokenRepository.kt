package com.bali.core.auth

import java.util.UUID

// refresh token 영속성을 위한 포트 인터페이스
interface RefreshTokenRepository {
    // refresh token을 저장하고 저장된 토큰을 반환
    fun save(token: RefreshToken): RefreshToken

    // 토큰 해시로 조회, 없으면 null 반환
    fun findByTokenHash(tokenHash: String): RefreshToken?

    // 지정한 id의 토큰이 아직 활성(미폐기) 상태일 때만 원자적으로 폐기 처리하고, 실제로 폐기했으면 true를 반환한다.
    // (동시에 같은 토큰으로 재발급이 시도되는 race condition에서 단 하나만 성공하도록 보장하기 위함)
    fun revokeIfActive(id: UUID): Boolean
}
