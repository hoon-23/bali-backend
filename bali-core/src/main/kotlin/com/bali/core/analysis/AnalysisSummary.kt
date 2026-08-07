package com.bali.core.analysis

import com.bali.core.exercise.MuscleGroup
import java.math.BigDecimal
import java.util.UUID

// 유저 1인의 최근 7일 운동 기록을 집계한 요약값
data class AnalysisSummary(
    val totalWorkoutMinutes: Int,
    val volumeByExercise: Map<UUID, BigDecimal>,
    val volumeByMuscleGroup: Map<MuscleGroup, BigDecimal>,
    val cardioTotalMinutes: Int,
    val completionRate: BigDecimal,
    val volumeChangeFromLastWeekPercent: BigDecimal?,
)
