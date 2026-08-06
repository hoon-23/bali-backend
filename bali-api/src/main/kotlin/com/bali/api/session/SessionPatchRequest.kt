package com.bali.api.session

import com.bali.api.template.TemplateItemRequest
import java.math.BigDecimal
import java.util.UUID

// 세션 아이템 구조 변경 요청. 언급되지 않은 log는 그대로 유지된다
data class SessionPatchRequest(
    val addItems: List<TemplateItemRequest> = emptyList(),
    val updateItems: List<SessionLogUpdateItem> = emptyList(),
    val removeLogIds: List<UUID> = emptyList(),
)

// 기존 log를 logId 기준으로 전체 교체하는 항목 (actual*/completed는 이 바디에 없으므로 항상 보존됨)
data class SessionLogUpdateItem(
    val logId: UUID,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
)
