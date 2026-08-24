package com.bali.core.analysis

import java.time.LocalDate

// 특정 날짜의 운동 집계 결과 (캘린더 히트맵/막대그래프용)
data class DailyStats(
    val date: LocalDate,
    val totalMinutes: Int,
    val sessionsCount: Int,
    val completedSets: Int,
)
