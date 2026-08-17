package com.bali.api.user

import com.bali.core.user.User
import com.bali.core.user.UserStatus
import java.util.UUID

// 사용자 정보를 HTTP 응답으로 변환하는 DTO
data class UserResponse(
    val id: UUID,
    val email: String,
    val nickname: String,
    val weeklyGoalSessions: Int,
    val consecutiveDays: Int,
    val status: UserStatus,
) {
    companion object {
        // User 도메인 모델 + 계산된 연속운동일을 UserResponse로 변환
        fun from(user: User, consecutiveDays: Int) = UserResponse(
            id = user.id!!,
            email = user.email,
            nickname = user.nickname,
            weeklyGoalSessions = user.weeklyGoalSessions,
            consecutiveDays = consecutiveDays,
            status = user.status,
        )
    }
}
