package com.bali.api.analysis

import com.bali.core.analysis.DailyStats
import java.time.LocalDate

// 특정 날짜의 운동 집계 결과 (캘린더 히트맵/막대그래프용)
data class DailyStatsResponse(
    val date: LocalDate,
    val totalMinutes: Int,
    val sessionsCount: Int,
    val completedSets: Int,
) {
    companion object {
        // DailyStats로부터 응답을 생성
        fun from(stats: DailyStats) = DailyStatsResponse(
            date = stats.date,
            totalMinutes = stats.totalMinutes,
            sessionsCount = stats.sessionsCount,
            completedSets = stats.completedSets,
        )
    }
}
