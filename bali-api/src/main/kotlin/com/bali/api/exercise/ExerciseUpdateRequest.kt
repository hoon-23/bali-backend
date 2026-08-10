package com.bali.api.exercise

import com.bali.core.exercise.MuscleGroup
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 개인 종목 수정 요청 바디 (type은 불변이라 포함하지 않음)
data class ExerciseUpdateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 255)
    val variant: String?,
    val muscleGroup: MuscleGroup,
)
