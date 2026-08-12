package com.bali.api.auth.login

import com.bali.core.user.AuthProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// 소셜 로그인 요청 바디. email은 Apple 최초 로그인에만 클라이언트가 채우고, 다른 provider는 무시된다.
// 이 email은 클라이언트가 그대로 보낸 값이라 서명 검증된 provider 응답의 email(SocialUserInfo.email)
// 만큼 신뢰할 수 없다 - 형식만 최소한으로 걸러낸다
data class SocialLoginRequest(
    val provider: AuthProvider,
    @field:NotBlank val token: String,
    @field:Email val email: String? = null,
)
