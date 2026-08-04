package com.bali.core.user

import java.time.Instant
import java.util.UUID

// 인증 및 상태 정보를 가진 사용자를 나타내는 도메인 모델
data class User(
    val id: UUID?,
    val email: String,
    val provider: AuthProvider,
    val providerId: String,
    val status: UserStatus,
    val createdAt: Instant,
) {
    // 사용자 상태를 WITHDRAWN으로 전환 (탈퇴 처리)
    fun withdraw(): User = copy(status = UserStatus.WITHDRAWN)
}
