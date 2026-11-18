package com.bali.core.session

import java.time.Instant
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
    // 세션 생성 시각. 신규 저장 시 null이면 저장 시각으로 채워진다
    val createdAt: Instant? = null,
    // COMPLETED로 전이된 시각. 완료 전이면 null
    val completedAt: Instant? = null,
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

// 중단(ABANDONED)된 세션들의 로그 id 집합. 분석의 완료율 집계에서 이 로그들을 제외하려고 쓴다
fun List<WorkoutSession>.abandonedLogIds(): Set<UUID> =
    filter { it.status == SessionStatus.ABANDONED }.flatMap { it.logs }.mapNotNull { it.id }.toSet()
