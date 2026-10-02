package com.bali.api.analysis

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.AnalysisSummary
import com.bali.core.analysis.WeeklyAnalysis
import com.bali.core.exercise.MuscleGroup
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// 주간 분석 결과를 HTTP 응답으로 변환하는 DTO
data class WeeklyAnalysisResponse(
    val weekOf: LocalDate,
    val status: AnalysisStatus,
    val summary: AnalysisSummaryResponse?,
    val insights: List<String>,
) {
    companion object {
        // WeeklyAnalysis 도메인 모델을 WeeklyAnalysisResponse로 변환
        fun from(analysis: WeeklyAnalysis) = WeeklyAnalysisResponse(
            weekOf = analysis.weekOf,
            status = analysis.status,
            summary = analysis.summary?.let { AnalysisSummaryResponse.from(it) },
            insights = analysis.insights.map { it.summaryText },
        )
    }
}

// 주간 집계 요약값을 HTTP 응답으로 변환하는 DTO
data class AnalysisSummaryResponse(
    val totalWorkoutMinutes: Int,
    val volumeByExercise: Map<UUID, BigDecimal>,
    val volumeByMuscleGroup: Map<MuscleGroup, BigDecimal>,
    val cardioTotalMinutes: Int,
    val completionRate: BigDecimal,
    val volumeChangeFromLastWeekPercent: BigDecimal?,
    val bodyweightRepsByExercise: Map<UUID, Int>,
    val setsByMuscleGroup: Map<MuscleGroup, Int>,
    val bodyweightRepsChangeFromLastWeekPercent: BigDecimal?,
    // 운동 세션 수/직전 기간 운동 세션 수/볼륨이 있는 세션 수/맨몸 반복수가 있는 세션 수. 2026-10-02 이전에 저장된 요약은 null
    val sessionCount: Int?,
    val previousSessionCount: Int?,
    val weightedSessionCount: Int?,
    val bodyweightSessionCount: Int?,
) {
    companion object {
        // AnalysisSummary 도메인 모델을 AnalysisSummaryResponse로 변환
        fun from(summary: AnalysisSummary) = AnalysisSummaryResponse(
            totalWorkoutMinutes = summary.totalWorkoutMinutes,
            volumeByExercise = summary.volumeByExercise,
            volumeByMuscleGroup = summary.volumeByMuscleGroup,
            cardioTotalMinutes = summary.cardioTotalMinutes,
            completionRate = summary.completionRate,
            volumeChangeFromLastWeekPercent = summary.volumeChangeFromLastWeekPercent,
            bodyweightRepsByExercise = summary.bodyweightRepsByExercise,
            setsByMuscleGroup = summary.setsByMuscleGroup,
            bodyweightRepsChangeFromLastWeekPercent = summary.bodyweightRepsChangeFromLastWeekPercent,
            sessionCount = summary.sessionCount,
            previousSessionCount = summary.previousSessionCount,
            weightedSessionCount = summary.weightedSessionCount,
            bodyweightSessionCount = summary.bodyweightSessionCount,
        )
    }
}
