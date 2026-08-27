package com.bali.core.exercise

import java.util.UUID

// 운동 종목을 나타내는 도메인 모델 (글로벌 시드 또는 사용자 개인 종목)
data class Exercise(
    val id: UUID?,
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val scope: ExerciseScope,
    val ownerId: UUID?,
    val equipment: Equipment? = null,
) {
    init {
        // scope와 ownerId의 일관성을 생성 시점에 강제
        when (scope) {
            ExerciseScope.PERSONAL -> require(ownerId != null) { "PERSONAL exercise requires an ownerId" }
            ExerciseScope.GLOBAL -> require(ownerId == null) { "GLOBAL exercise must not have an ownerId" }
        }
    }
}
