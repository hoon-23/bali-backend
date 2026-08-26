package com.bali.core.template

import com.bali.core.exercise.ExerciseType
import java.math.BigDecimal
import java.util.UUID

// 템플릿에 속한 운동 항목 (계획된 목표치)
data class TemplateItem(
    val id: UUID?,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?,
    val targetPace: String?,
) {
    companion object {
        // exerciseType에 맞는 target 필드 조합만 허용하는 검증된 TemplateItem 생성
        fun create(
            exerciseType: ExerciseType,
            exerciseId: UUID,
            sortOrder: Int,
            targetSets: Int? = null,
            targetReps: Int? = null,
            targetWeight: BigDecimal? = null,
            targetDurationSeconds: Int? = null,
            targetPace: String? = null,
        ): TemplateItem {
            exerciseType.requireNoForeignGroupFields(
                hasStrengthField = targetSets != null || targetReps != null || targetWeight != null,
                hasCardioField = targetDurationSeconds != null || targetPace != null,
                strengthFieldNames = "targetSets/targetReps/targetWeight",
                cardioFieldNames = "targetDurationSeconds/targetPace",
            )
            when (exerciseType) {
                ExerciseType.STRENGTH -> require(targetSets != null && targetReps != null && targetWeight != null) {
                    "STRENGTH exercise requires targetSets/targetReps/targetWeight"
                }
                ExerciseType.CARDIO -> require(targetDurationSeconds != null) { "CARDIO exercise requires targetDurationSeconds" }
            }
            return TemplateItem(
                id = null,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeight = targetWeight,
                targetDurationSeconds = targetDurationSeconds,
                targetPace = targetPace,
            )
        }
    }
}
