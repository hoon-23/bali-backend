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
)
