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
    val nickname: String = "",
    val weeklyGoalSessions: Int = 3,
) {
    // 사용자 상태를 WITHDRAWN으로 전환 (탈퇴 처리)
    fun withdraw(): User = copy(status = UserStatus.WITHDRAWN)

    // 닉네임/주간 목표 운동 횟수를 부분 수정한다 (null인 필드는 기존 값 유지). 유효하지 않은 값이면 예외
    fun updateProfile(nickname: String?, weeklyGoalSessions: Int?): User {
        val newNickname = (nickname ?: this.nickname).trim()
        require(newNickname.isNotEmpty()) { "nickname must not be blank" }
        require(newNickname.length <= 20) { "nickname must be at most 20 characters" }
        val newWeeklyGoalSessions = weeklyGoalSessions ?: this.weeklyGoalSessions
        require(newWeeklyGoalSessions in 1..7) { "weeklyGoalSessions must be between 1 and 7" }
        return copy(nickname = newNickname, weeklyGoalSessions = newWeeklyGoalSessions)
    }
}
