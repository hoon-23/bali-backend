package com.bali.api.session

import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// 운동 세션 정보를 HTTP 응답으로 변환하는 DTO
data class SessionResponse(
    val id: UUID,
    val date: LocalDate,
    val templateId: UUID?,
    val logs: List<SessionLogResponse>,
) {
    companion object {
        // WorkoutSession 도메인 모델을 SessionResponse로 변환
        fun from(session: WorkoutSession) = SessionResponse(
            id = session.id!!,
            date = session.date,
            templateId = session.templateId,
            logs = session.logs.map { SessionLogResponse.from(it) },
        )
    }
}

// 세션 log 정보를 HTTP 응답으로 변환하는 DTO
data class SessionLogResponse(
    val id: UUID,
    val exerciseId: UUID,
    val sortOrder: Int,
    val completed: Boolean,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
) {
    companion object {
        // SessionLog 도메인 모델을 SessionLogResponse로 변환
        fun from(log: SessionLog) = SessionLogResponse(
            id = log.id!!, exerciseId = log.exerciseId, sortOrder = log.sortOrder, completed = log.completed,
            targetSets = log.targetSets, targetReps = log.targetReps, targetWeight = log.targetWeight,
            targetDurationSeconds = log.targetDurationSeconds, targetPace = log.targetPace,
            actualSets = log.actualSets, actualReps = log.actualReps, actualWeight = log.actualWeight,
            actualDurationSeconds = log.actualDurationSeconds, actualPace = log.actualPace,
        )
    }
}
