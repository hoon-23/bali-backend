package com.bali.api.exercise

import com.bali.core.session.ExerciseLastRecord
import java.math.BigDecimal
import java.util.UUID

// 종목별 마지막 수행 기록 응답 (근력은 sets/reps/weight, 유산소는 durationSeconds만 채워진다)
data class ExerciseLastRecordResponse(
    val exerciseId: UUID,
    val sets: Int?,
    val reps: Int?,
    val weight: BigDecimal?,
    val durationSeconds: Int?,
) {
    companion object {
        fun from(record: ExerciseLastRecord) = ExerciseLastRecordResponse(
            exerciseId = record.exerciseId,
            sets = record.sets,
            reps = record.reps,
            weight = record.weight,
            durationSeconds = record.durationSeconds,
        )
    }
}
