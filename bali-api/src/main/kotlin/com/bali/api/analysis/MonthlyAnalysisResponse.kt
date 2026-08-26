package com.bali.api.analysis

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysis
import java.time.LocalDate

// 월간 분석 결과를 HTTP 응답으로 변환하는 DTO
data class MonthlyAnalysisResponse(
    val monthOf: LocalDate,
    val status: AnalysisStatus,
    val summary: AnalysisSummaryResponse?,
    val insights: List<String>,
) {
    companion object {
        // MonthlyAnalysis 도메인 모델을 MonthlyAnalysisResponse로 변환
        fun from(analysis: MonthlyAnalysis) = MonthlyAnalysisResponse(
            monthOf = analysis.monthOf,
            status = analysis.status,
            summary = analysis.summary?.let { AnalysisSummaryResponse.from(it) },
            insights = analysis.insights.map { it.summaryText },
        )
    }
}
