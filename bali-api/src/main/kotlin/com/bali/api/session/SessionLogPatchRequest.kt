package com.bali.api.session

import java.math.BigDecimal

// 실제 수행값 기록 + 완료 체크 요청 바디. null인 필드는 변경하지 않는다
data class SessionLogPatchRequest(
    val completed: Boolean?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
)
