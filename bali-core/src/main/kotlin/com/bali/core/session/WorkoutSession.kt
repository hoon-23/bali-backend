package com.bali.core.session

import java.time.LocalDate
import java.util.UUID

// 특정 날짜에 실제로 수행한 운동 세션 기록
data class WorkoutSession(
    val id: UUID?,
    val userId: UUID,
    val date: LocalDate,
    val templateId: UUID?,
    val status: SessionStatus,
    val logs: List<SessionLog>,
    // 운동 종료 후 사용자에게 물어보는 체감 난이도(RPE). 세션 전체 1개, 종료 전엔 null
    val perceivedDifficulty: Int? = null,
) {
    companion object {
        // PATCH로 들어온 perceivedDifficulty가 유효 범위(1~10)인지 검증
        fun validatePerceivedDifficulty(perceivedDifficulty: Int?) {
            require(perceivedDifficulty == null || perceivedDifficulty in 1..10) {
                "perceivedDifficulty must be between 1 and 10"
            }
        }
    }
}
