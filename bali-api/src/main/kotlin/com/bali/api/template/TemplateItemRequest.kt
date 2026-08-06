package com.bali.api.template

import java.math.BigDecimal
import java.util.UUID

// 템플릿 항목 요청 (STRENGTH/CARDIO 필드 검증은 TemplateItem.create가 수행)
data class TemplateItemRequest(
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?,
    val targetPace: String?,
)
