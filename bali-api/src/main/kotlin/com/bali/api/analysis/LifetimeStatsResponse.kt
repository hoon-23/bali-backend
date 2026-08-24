package com.bali.api.analysis

// 가입 이후 누적 운동일수/운동시간
data class LifetimeStatsResponse(
    val totalWorkoutDays: Int,
    val totalWorkoutMinutes: Int,
)
