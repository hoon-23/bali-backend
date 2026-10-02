package com.bali.core.analysis

import com.bali.core.exercise.MuscleGroup
import java.math.BigDecimal
import java.util.UUID

// 유저 1인의 한 기간(주/월) 운동 기록을 집계한 요약값. JSONB로 저장되므로 나중에 추가한 필드는 기본값을 둬 구버전 행도 읽히게 한다
data class AnalysisSummary(
    val totalWorkoutMinutes: Int,
    val volumeByExercise: Map<UUID, BigDecimal>,
    val volumeByMuscleGroup: Map<MuscleGroup, BigDecimal>,
    val cardioTotalMinutes: Int,
    val completionRate: BigDecimal,
    // 세션당 평균 볼륨의 직전 기간 대비 증감률(%). 2026-10-02 이전에 저장된 요약은 총 볼륨 기준이다
    val volumeChangeFromLastWeekPercent: BigDecimal?,
    val bodyweightRepsByExercise: Map<UUID, Int> = emptyMap(),
    val setsByMuscleGroup: Map<MuscleGroup, Int> = emptyMap(),
    // 세션당 평균 맨몸 반복수의 직전 기간 대비 증감률(%). 2026-10-02 이전에 저장된 요약은 총 반복수 기준이다
    val bodyweightRepsChangeFromLastWeekPercent: BigDecimal? = null,
    // 운동 세션 수(완료 로그가 1개 이상인 세션, 상태 무관). 구버전 요약은 null
    val sessionCount: Int? = null,
    // 직전 기간의 운동 세션 수. 직전 분석이 없거나 실패했거나 구버전이면 null
    val previousSessionCount: Int? = null,
    // 볼륨이 0보다 큰 세션 수 (세션당 볼륨의 분모)
    val weightedSessionCount: Int? = null,
    // 맨몸 반복수가 0보다 큰 세션 수 (세션당 맨몸 반복수의 분모)
    val bodyweightSessionCount: Int? = null,
)
