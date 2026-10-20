package com.bali.core.auth

import java.util.UUID

// Apple 로그인 사용자의 Apple refresh token(탈퇴 시 revoke용) 영속성을 위한 포트 인터페이스
interface AppleRefreshTokenRepository {
    // 유저의 Apple refresh token을 저장한다. 이미 있으면 새 값으로 교체한다
    fun save(userId: UUID, refreshToken: String)

    // 유저의 Apple refresh token 조회, 없으면 null 반환
    fun findByUserId(userId: UUID): String?

    // 유저의 Apple refresh token 삭제 (없어도 무시)
    fun deleteByUserId(userId: UUID)
}
