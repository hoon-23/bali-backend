package com.bali.core.analysis

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseType
import com.bali.core.session.SessionLog
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

// SessionLog 목록 + 종목 메타데이터로부터 주간 집계값/규칙 기반 인사이트를 계산하는 순수 함수 모음
object WeeklyStatsCalculator {

    // 완료된 STRENGTH 로그 1개당 추정 소요 시간(분). 사용자 실측 기록(휴식 제외 종목당 10~15분) 기반 중간값
    private const val MINUTES_PER_STRENGTH_EXERCISE = 12
    private val WOW_CHANGE_THRESHOLD = BigDecimal(10)
    private val LOW_COMPLETION_THRESHOLD = BigDecimal(70)
    private val LOW_MUSCLE_GROUP_SHARE_THRESHOLD = BigDecimal(15)

    // 최근 7일 SessionLog 목록으로부터 AnalysisSummary를 계산. exercisesById는 logs에 등장하는 모든 exerciseId를 커버해야 함
    fun calculate(
        logs: List<SessionLog>,
        exercisesById: Map<UUID, Exercise>,
        previousSummary: AnalysisSummary?,
    ): AnalysisSummary {
        val completedLogs = logs.filter { it.completed }
        val strengthLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.STRENGTH }
        val cardioLogs = completedLogs.filter { exercisesById.getValue(it.exerciseId).type == ExerciseType.CARDIO }

        val volumeByExercise = strengthLogs
            .groupBy { it.exerciseId }
            .mapValues { (_, exerciseLogs) -> exerciseLogs.fold(BigDecimal.ZERO) { acc, log -> acc + volumeOf(log) } }

        val volumeByMuscleGroup = strengthLogs
            .groupBy { exercisesById.getValue(it.exerciseId).muscleGroup }
            .mapValues { (_, groupLogs) -> groupLogs.fold(BigDecimal.ZERO) { acc, log -> acc + volumeOf(log) } }

        val cardioTotalMinutes = cardioLogs.sumOf { it.actualDurationSeconds ?: 0 } / 60
        val totalWorkoutMinutes = strengthLogs.size * MINUTES_PER_STRENGTH_EXERCISE + cardioTotalMinutes

        val completionRate = if (logs.isEmpty()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(completedLogs.size).multiply(BigDecimal(100))
                .divide(BigDecimal(logs.size), 1, RoundingMode.HALF_UP)
        }

        val volumeChangeFromLastWeekPercent = previousSummary?.let { prev ->
            val previousTotal = prev.volumeByExercise.values.fold(BigDecimal.ZERO, BigDecimal::add)
            if (previousTotal.compareTo(BigDecimal.ZERO) == 0) {
                null
            } else {
                val currentTotal = volumeByExercise.values.fold(BigDecimal.ZERO, BigDecimal::add)
                currentTotal.subtract(previousTotal).multiply(BigDecimal(100))
                    .divide(previousTotal, 1, RoundingMode.HALF_UP)
            }
        }

        return AnalysisSummary(
            totalWorkoutMinutes = totalWorkoutMinutes,
            volumeByExercise = volumeByExercise,
            volumeByMuscleGroup = volumeByMuscleGroup,
            cardioTotalMinutes = cardioTotalMinutes,
            completionRate = completionRate,
            volumeChangeFromLastWeekPercent = volumeChangeFromLastWeekPercent,
        )
    }

    // SessionLog 하나의 볼륨(무게*횟수*세트)을 계산
    private fun volumeOf(log: SessionLog): BigDecimal {
        val sets = BigDecimal(log.actualSets ?: 0)
        val reps = BigDecimal(log.actualReps ?: 0)
        val weight = log.actualWeight ?: BigDecimal.ZERO
        return sets.multiply(reps).multiply(weight)
    }

    // AnalysisSummary로부터 규칙 기반 인사이트 문장을 생성 (조건 미충족 규칙은 문장을 만들지 않음)
    fun generateInsights(summary: AnalysisSummary): List<Insight> {
        val insights = mutableListOf<Insight>()

        summary.volumeChangeFromLastWeekPercent?.let { change ->
            when {
                change >= WOW_CHANGE_THRESHOLD ->
                    insights += Insight(id = null, summaryText = "이번 주 볼륨이 지난주보다 ${change}% 증가했어요")
                change <= WOW_CHANGE_THRESHOLD.negate() ->
                    insights += Insight(id = null, summaryText = "이번 주 볼륨이 지난주보다 ${change.abs()}% 감소했어요")
            }
        }

        if (summary.completionRate < LOW_COMPLETION_THRESHOLD) {
            insights += Insight(id = null, summaryText = "완료율이 ${summary.completionRate}%로 낮은 편이에요")
        }

        val totalMuscleVolume = summary.volumeByMuscleGroup.values.fold(BigDecimal.ZERO, BigDecimal::add)
        if (summary.volumeByMuscleGroup.size >= 2 && totalMuscleVolume.compareTo(BigDecimal.ZERO) > 0) {
            val leastEntry = summary.volumeByMuscleGroup.entries.minBy { it.value }
            val share = leastEntry.value.multiply(BigDecimal(100)).divide(totalMuscleVolume, 1, RoundingMode.HALF_UP)
            if (share < LOW_MUSCLE_GROUP_SHARE_THRESHOLD) {
                insights += Insight(id = null, summaryText = "${leastEntry.key.displayName} 비중이 ${share}%로 낮은 편이에요")
            }
        }

        return insights
    }
}
