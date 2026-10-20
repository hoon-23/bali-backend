package com.bali.api.auth.login

import com.bali.api.auth.social.AppleAccountService
import com.bali.api.auth.social.AppleAuthClient
import com.bali.core.auth.AppleRefreshTokenRepository
import java.util.UUID

// 로그인 테스트에서 Apple 연동을 쓰지 않을 때 넘기는 아무 것도 하지 않는 AppleAccountService
fun noopAppleAccountService(): AppleAccountService = AppleAccountService(
    object : AppleAuthClient {
        override fun exchangeCode(authorizationCode: String): String? = null
        override fun revoke(refreshToken: String): Boolean = false
    },
    object : AppleRefreshTokenRepository {
        override fun save(userId: UUID, refreshToken: String) {}
        override fun findByUserId(userId: UUID): String? = null
        override fun deleteByUserId(userId: UUID) {}
    },
)
