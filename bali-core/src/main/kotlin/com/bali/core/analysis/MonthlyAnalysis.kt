package com.bali.core.analysis

import java.time.LocalDate
import java.util.UUID

// 유저별 월간 운동 분석 결과. SUCCESS면 summary가 반드시 있고, FAILED/NO_ACTIVITY면 summary/insights가 비어있어야 함
data class MonthlyAnalysis(
    val id: UUID?,
    val userId: UUID,
    val monthOf: LocalDate,
    val status: AnalysisStatus,
    val summary: AnalysisSummary?,
    val insights: List<Insight>,
) {
    init {
        // status와 summary/insights의 일관성을 생성 시점에 강제
        when (status) {
            AnalysisStatus.SUCCESS -> require(summary != null) { "SUCCESS status requires a non-null summary" }
            AnalysisStatus.FAILED, AnalysisStatus.NO_ACTIVITY -> {
                require(summary == null) { "FAILED/NO_ACTIVITY status must not have a summary" }
                require(insights.isEmpty()) { "FAILED/NO_ACTIVITY status must not have insights" }
            }
        }
    }
}
