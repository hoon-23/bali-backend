package com.bali.api.auth.login

import jakarta.validation.constraints.NotBlank

// refresh/logout 요청 바디
data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)
