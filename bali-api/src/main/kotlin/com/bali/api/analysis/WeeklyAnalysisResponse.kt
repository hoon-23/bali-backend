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
        )
    }
}
