package com.bali.api.analysis

import com.bali.core.analysis.AnalysisSummary
import java.time.LocalDate

// 진행 중인 이번 주(월~일)의 실시간 집계 결과. 배치로 생성되는 WeeklyAnalysis와 달리 요청 시점에 즉석 계산되며 저장되지 않는다
data class CurrentWeekSummaryResponse(
    val weekOf: LocalDate,
    val totalWorkoutMinutes: Int,
    val strengthMinutes: Int,
    val cardioMinutes: Int,
    val completedSessionsCount: Int,
) {
    companion object {
        // AnalysisSummary + 완료 세션 수로부터 응답을 생성. strengthMinutes는 totalWorkoutMinutes에서
        // cardioMinutes를 뺀 값이다 (WeeklyStatsCalculator의 계산식: total = strength + cardio)
        fun from(weekOf: LocalDate, summary: AnalysisSummary, completedSessionsCount: Int) = CurrentWeekSummaryResponse(
            weekOf = weekOf,
            totalWorkoutMinutes = summary.totalWorkoutMinutes,
            strengthMinutes = summary.totalWorkoutMinutes - summary.cardioTotalMinutes,
            cardioMinutes = summary.cardioTotalMinutes,
            completedSessionsCount = completedSessionsCount,
        )
    }
}
