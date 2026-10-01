package com.bali.api.template

import com.bali.core.template.TemplateCategory
import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// 운동 템플릿 정보를 HTTP 응답으로 변환하는 DTO
data class TemplateResponse(
    val id: UUID,
    val category: TemplateCategory,
    val name: String,
    val items: List<TemplateItemResponse>,
    // 이 템플릿으로 만든 가장 최근 완료 세션의 날짜. 완료 이력이 없으면 null
    val lastUsedAt: LocalDate? = null,
) {
    companion object {
        // WorkoutTemplate 도메인 모델을 TemplateResponse로 변환 (lastUsedAt은 호출자가 조회해서 전달)
        fun from(template: WorkoutTemplate, lastUsedAt: LocalDate? = null) = TemplateResponse(
            id = template.id!!,
            category = template.category,
            name = template.name,
            items = template.items.map { TemplateItemResponse.from(it) },
            lastUsedAt = lastUsedAt,
        )
    }
}

// 템플릿 항목 정보를 HTTP 응답으로 변환하는 DTO
data class TemplateItemResponse(
    val id: UUID,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?,
    val targetPace: String?,
) {
    companion object {
        // TemplateItem 도메인 모델을 TemplateItemResponse로 변환
        fun from(item: TemplateItem) = TemplateItemResponse(
            id = item.id!!,
            exerciseId = item.exerciseId,
            sortOrder = item.sortOrder,
            targetSets = item.targetSets,
            targetReps = item.targetReps,
            targetWeight = item.targetWeight,
            targetDurationSeconds = item.targetDurationSeconds,
            targetPace = item.targetPace,
        )
    }
}
