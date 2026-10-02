package com.bali.api.user

import com.bali.core.user.User
import com.bali.core.user.UserLevel
import com.bali.core.user.UserStatus
import java.util.UUID

// 사용자 정보를 HTTP 응답으로 변환하는 DTO
data class UserResponse(
    val id: UUID,
    val email: String,
    val nickname: String,
    val weeklyGoalSessions: Int,
    val weeklyWorkoutDays: Int,
    val status: UserStatus,
    val level: UserLevelResponse,
) {
    companion object {
        // User 도메인 모델 + 계산된 이번 주 운동일수/레벨을 UserResponse로 변환
        fun from(user: User, weeklyWorkoutDays: Int, level: UserLevel) = UserResponse(
            level = UserLevelResponse(level.level, level.currentXp, level.xpForNextLevel, level.totalXp),
            id = user.id!!,
            email = user.email,
            nickname = user.nickname,
            weeklyGoalSessions = user.weeklyGoalSessions,
            weeklyWorkoutDays = weeklyWorkoutDays,
            status = user.status,
        )
    }
}

// 레벨/경험치 응답. 진행 바는 currentXp / xpForNextLevel로 그린다 (레벨 곡선 계산은 서버가 담당)
data class UserLevelResponse(
    val level: Int,
    val currentXp: Int,
    val xpForNextLevel: Int,
    val totalXp: Int,
)
