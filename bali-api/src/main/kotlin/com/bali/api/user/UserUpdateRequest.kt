package com.bali.api.user

// 프로필 부분 수정 요청 바디 (모두 optional — null이면 기존 값 유지, 형식 검증은 User.updateProfile이 담당,
// 이메일 중복 검증은 DB 조회가 필요해 컨트롤러가 담당)
data class UserUpdateRequest(
    val nickname: String?,
    val weeklyGoalSessions: Int?,
    val email: String?,
)
