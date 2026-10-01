package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

// 맨몸 운동 반복수/근육군별 세트 수 집계와 관련 인사이트를 계산하는 주간/월간 공용 순수 함수 모음
object BodyweightStats {

    private val REPS_CHANGE_THRESHOLD = BigDecimal(10)
    private val LOW_MUSCLE_GROUP_SHARE_THRESHOLD = BigDecimal(15)

    // 완료된 STRENGTH 로그 중 BODYWEIGHT 종목의 종목별 총 반복수(sets*reps). 0인 종목은 제외
    fun repsByExercise(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<UUID, Int> =
        strengthLogs
            .filter { exercisesById.getValue(it.exerciseId).equipment == Equipment.BODYWEIGHT }
            .groupBy { it.exerciseId }
            .mapValues { (_, logs) -> logs.sumOf { (it.actualSets ?: 0) * (it.actualReps ?: 0) } }
            .filterValues { it > 0 }

    // 완료된 STRENGTH 로그의 근육군별 세트 수(중량/맨몸 구분 없음). 0인 근육군은 제외
    fun setsByMuscleGroup(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<MuscleGroup, Int> =
        strengthLogs
            .groupBy { exercisesById.getValue(it.exerciseId).muscleGroup }
            .mapValues { (_, logs) -> logs.sumOf { it.actualSets ?: 0 } }
            .filterValues { it > 0 }

    // 맨몸 총 반복수의 직전 기간 대비 증감률(%). 직전 총합이 0이면 null
    fun repsChangePercent(current: Map<UUID, Int>, previous: Map<UUID, Int>): BigDecimal? {
        val previousTotal = previous.values.sum()
        if (previousTotal == 0) return null
        return BigDecimal(current.values.sum() - previousTotal).multiply(BigDecimal(100))
            .divide(BigDecimal(previousTotal), 1, RoundingMode.HALF_UP)
    }

    // 증감률이 ±10% 이상일 때만 인사이트를 만든다 (period 예: "이번 주", previousPeriod 예: "지난주")
    fun repsChangeInsight(change: BigDecimal?, period: String, previousPeriod: String): Insight? = when {
        change == null -> null
        change >= REPS_CHANGE_THRESHOLD ->
            Insight(id = null, summaryText = "$period 맨몸 운동 반복수가 ${previousPeriod}보다 ${change}% 증가했어요")
        change <= REPS_CHANGE_THRESHOLD.negate() ->
            Insight(id = null, summaryText = "$period 맨몸 운동 반복수가 ${previousPeriod}보다 ${change.abs()}% 감소했어요")
        else -> null
    }

    // 근육군이 2개 이상일 때 세트 비중이 가장 낮은 근육군이 15% 미만이면 불균형 인사이트를 만든다
    fun muscleShareInsight(setsByMuscleGroup: Map<MuscleGroup, Int>): Insight? {
        val total = setsByMuscleGroup.values.sum()
        if (setsByMuscleGroup.size < 2 || total == 0) return null
        val least = setsByMuscleGroup.entries.minBy { it.value }
        val share = BigDecimal(least.value).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP)
        return if (share < LOW_MUSCLE_GROUP_SHARE_THRESHOLD) {
            Insight(id = null, summaryText = "${least.key.displayName} 비중이 ${share}%로 낮은 편이에요")
        } else {
            null
        }
    }
}
