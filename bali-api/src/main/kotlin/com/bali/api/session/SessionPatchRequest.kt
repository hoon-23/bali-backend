package com.bali.api.session

import com.bali.api.template.TemplateItemRequest
import com.bali.core.session.SessionStatus
import java.math.BigDecimal
import java.util.UUID

// 세션 아이템 구조 변경 + status 전이 요청. 언급되지 않은 log는 그대로 유지된다.
// status는 null이면 변경하지 않으며, 전이는 클라이언트가 명시적으로 지정한다(서버가 date로부터 추론하지 않음)
data class SessionPatchRequest(
    val addItems: List<TemplateItemRequest> = emptyList(),
    val updateItems: List<SessionLogUpdateItem> = emptyList(),
    val removeLogIds: List<UUID> = emptyList(),
    val status: SessionStatus? = null,
    val perceivedDifficulty: Int? = null,
)

// 기존 log를 logId 기준으로 전체 교체하는 항목 (exerciseId가 바뀌면 completed/actual*는 초기화됨 — 종목이 바뀌면 이전 수행 기록은 무효해짐.
// exerciseId가 그대로면(sortOrder/target*만 변경) completed/actual*는 보존됨. 언급되지 않은 log 자체는 항상 보존됨)
data class SessionLogUpdateItem(
    val logId: UUID,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
)
