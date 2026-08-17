package com.bali.api.user

// 프로필 부분 수정 요청 바디 (둘 다 optional — null이면 기존 값 유지, 검증은 User.updateProfile이 담당)
data class UserUpdateRequest(
    val nickname: String?,
    val weeklyGoalSessions: Int?,
)
