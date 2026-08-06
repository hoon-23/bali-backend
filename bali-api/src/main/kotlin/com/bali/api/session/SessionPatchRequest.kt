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

// 기존 log를 logId 기준으로 전체 교체하는 항목 (exerciseId 변경 시 completed/actual*는 초기화됨 — 종목이 바뀌면 이전 수행 기록은 무효해짐. 언급되지 않은 log만 보존됨)
data class SessionLogUpdateItem(
    val logId: UUID,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
)
