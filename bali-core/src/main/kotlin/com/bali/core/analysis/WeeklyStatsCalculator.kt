package com.bali.core.analysis

import com.bali.core.exercise.Exercise
import com.bali.core.session.WorkoutSession
import java.util.UUID

// 주간 집계값/규칙 기반 인사이트 계산의 진입점. 계산은 주간/월간 공용 PeriodStatsCalculator가 하고 여기서는 주간 라벨만 정한다
object WeeklyStatsCalculator {

    // 주 2회 이상을 같은 횟수로 이어 가면 "꾸준해요" 문장을 만든다
    private val LABELS = PeriodLabels(current = "이번 주", previous = "지난주", steadyMinSessions = 2)

    // 한 주(월~일)의 세션 목록으로부터 AnalysisSummary를 계산. exercisesById는 세션 로그에 등장하는 모든 exerciseId를 커버해야 함
    fun calculate(
        sessions: List<WorkoutSession>,
        exercisesById: Map<UUID, Exercise>,
        previousSummary: AnalysisSummary?,
        previousSessionCount: Int? = null,
    ): AnalysisSummary = PeriodStatsCalculator.calculate(sessions, exercisesById, previousSummary, previousSessionCount)

    // AnalysisSummary로부터 주간 인사이트 문장을 생성
    fun generateInsights(summary: AnalysisSummary): List<Insight> = PeriodStatsCalculator.generateInsights(summary, LABELS)
}
