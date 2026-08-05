package com.bali.api.exercise

import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup

// 개인 종목 등록 요청 바디
data class ExerciseCreateRequest(
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
)
