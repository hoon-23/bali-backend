package com.bali.api.template

import com.bali.core.template.TemplateCategory
import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
import java.math.BigDecimal
import java.util.UUID

// 운동 템플릿 정보를 HTTP 응답으로 변환하는 DTO
data class TemplateResponse(
    val id: UUID,
    val category: TemplateCategory,
    val name: String,
    val items: List<TemplateItemResponse>,
) {
    companion object {
        // WorkoutTemplate 도메인 모델을 TemplateResponse로 변환
        fun from(template: WorkoutTemplate) = TemplateResponse(
            id = template.id!!,
            category = template.category,
            name = template.name,
            items = template.items.map { TemplateItemResponse.from(it) },
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
