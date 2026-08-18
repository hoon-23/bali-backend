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
    // 사용자 상태를 WITHDRAWN으로 전환하고 식별 가능한 개인정보(email/providerId/nickname)를 파기한다
    // (PIPA 제21조 파기 의무). providerId가 바뀌므로 이후 같은 provider 계정으로 재로그인하면
    // 기존 계정과 매칭되지 않고 신규 계정으로 생성된다 — 탈퇴를 실제로 되돌릴 수 없게 만드는 의도된 부수효과
    fun withdraw(): User = copy(
        status = UserStatus.WITHDRAWN,
        email = "withdrawn-$id@bali.internal",
        providerId = "withdrawn-$id",
        nickname = "탈퇴한사용자",
    )

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
