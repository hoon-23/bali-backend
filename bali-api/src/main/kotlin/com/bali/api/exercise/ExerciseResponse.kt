package com.bali.api.exercise

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import java.util.UUID

// 운동 종목 정보를 HTTP 응답으로 변환하는 DTO
data class ExerciseResponse(
    val id: UUID,
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val muscleGroupDisplayName: String,
    val type: ExerciseType,
    val scope: ExerciseScope,
) {
    companion object {
        // Exercise 도메인 모델을 ExerciseResponse로 변환
        fun from(exercise: Exercise) = ExerciseResponse(
            id = exercise.id!!,
            name = exercise.name,
            variant = exercise.variant,
            muscleGroup = exercise.muscleGroup,
            muscleGroupDisplayName = exercise.muscleGroup.displayName,
            type = exercise.type,
            scope = exercise.scope,
        )
    }
}
