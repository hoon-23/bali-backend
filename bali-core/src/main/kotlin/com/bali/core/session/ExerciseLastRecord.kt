package com.bali.core.session

import java.math.BigDecimal
import java.util.UUID

// 종목을 마지막으로 실제 수행한 기록값. 루틴에 종목을 추가할 때 기본 목표값으로 미리 채우는 용도
data class ExerciseLastRecord(
    val exerciseId: UUID,
    val sets: Int?,
    val reps: Int?,
    val weight: BigDecimal?,
    val durationSeconds: Int?,
)
