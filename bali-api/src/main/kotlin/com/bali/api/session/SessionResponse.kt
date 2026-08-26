package com.bali.api.session

import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.SetTiming
import com.bali.core.session.WorkoutSession
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// 운동 세션 정보를 HTTP 응답으로 변환하는 DTO
data class SessionResponse(
    val id: UUID,
    val date: LocalDate,
    val templateId: UUID?,
    val status: SessionStatus,
    val logs: List<SessionLogResponse>,
    val perceivedDifficulty: Int?,
    val title: String,
) {
    companion object {
        // WorkoutSession 도메인 모델 + 서버에서 미리 계산한 title(템플릿 이름 또는 종목 이름 조합)을 SessionResponse로 변환
        fun from(session: WorkoutSession, title: String) = SessionResponse(
            id = session.id!!,
            date = session.date,
            templateId = session.templateId,
            status = session.status,
            logs = session.logs.map { SessionLogResponse.from(it) },
            perceivedDifficulty = session.perceivedDifficulty,
            title = title,
        )
    }
}

// 세션 목록 커서 페이지 응답. nextCursor는 hasNext가 true일 때만 값이 있고, 그대로 다음 요청의 cursor 파라미터에 넣으면 된다
data class SessionPageResponse(
    val content: List<SessionResponse>,
    val hasNext: Boolean,
    val nextCursor: String?,
)

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
    val setTimings: List<SetTimingResponse>?,
) {
    companion object {
        // SessionLog 도메인 모델을 SessionLogResponse로 변환
        fun from(log: SessionLog) = SessionLogResponse(
            id = log.id!!, exerciseId = log.exerciseId, sortOrder = log.sortOrder, completed = log.completed,
            targetSets = log.targetSets, targetReps = log.targetReps, targetWeight = log.targetWeight,
            targetDurationSeconds = log.targetDurationSeconds, targetPace = log.targetPace,
            actualSets = log.actualSets, actualReps = log.actualReps, actualWeight = log.actualWeight,
            actualDurationSeconds = log.actualDurationSeconds, actualPace = log.actualPace,
            setTimings = log.setTimings?.map { SetTimingResponse.from(it) },
        )
    }
}

// 세트 하나의 시작/종료 시각 응답 DTO. 원본 그대로 반환하며 휴식시간/총시간 계산은 하지 않는다(클라이언트 담당)
data class SetTimingResponse(val setIndex: Int, val startedAt: Instant, val endedAt: Instant) {
    companion object {
        fun from(setTiming: SetTiming) = SetTimingResponse(setTiming.setIndex, setTiming.startedAt, setTiming.endedAt)
    }
}
