package com.bali.api.exercise

import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 개인 종목 등록 요청 바디
data class ExerciseCreateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 255)
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
)
