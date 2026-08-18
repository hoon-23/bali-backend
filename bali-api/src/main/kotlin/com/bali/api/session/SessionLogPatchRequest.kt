package com.bali.api.session

import java.math.BigDecimal
import java.time.Instant

// 실제 수행값 기록 + 완료 체크 요청 바디. null인 필드는 변경하지 않는다 (setTimings는 non-null이면 전체 교체)
data class SessionLogPatchRequest(
    val completed: Boolean?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
    val setTimings: List<SetTimingRequest>?,
)

// 세트 하나의 시작/종료 시각 요청 DTO. 계산 로직 없이 그대로 도메인 모델로 변환된다
data class SetTimingRequest(val setIndex: Int, val startedAt: Instant, val endedAt: Instant)
