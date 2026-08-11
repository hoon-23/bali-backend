package com.bali.api.auth.login

import com.bali.core.user.AuthProvider
import jakarta.validation.constraints.NotBlank

// 소셜 로그인 요청 바디. email은 Apple 최초 로그인에만 클라이언트가 채우고, 다른 provider는 무시된다
data class SocialLoginRequest(
    val provider: AuthProvider,
    @field:NotBlank val token: String,
    val email: String? = null,
)
