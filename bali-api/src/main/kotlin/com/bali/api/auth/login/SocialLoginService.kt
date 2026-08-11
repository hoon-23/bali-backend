package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.social.SocialTokenVerifier
import com.bali.api.auth.social.SocialUserInfo
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.springframework.stereotype.Service
import java.time.Instant

// provider에 맞는 검증기를 골라 신원을 확인하고, 유저 조회/생성 + JWT 발급까지 담당
@Service
class SocialLoginService(
    private val verifiers: List<SocialTokenVerifier>,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    // provider 토큰을 검증해 기존/신규 유저를 확인하고 자체 JWT를 발급한다
    fun login(provider: AuthProvider, token: String, email: String?): String {
        val verifier = verifiers.first { it.provider == provider }
        val info = verifier.verify(token)

        val user = userRepository.findByProviderAndProviderId(provider, info.providerId)
            ?: createUser(provider, info, email)

        return jwtTokenProvider.generateToken(user.id!!, user.email)
    }

    // 최초 로그인 생성. email 우선순위: 토큰/API 응답값 > 요청 바디 값(Apple 최초 로그인) > placeholder
    private fun createUser(provider: AuthProvider, info: SocialUserInfo, requestEmail: String?): User {
        val email = info.email ?: requestEmail ?: placeholderEmail(provider, info.providerId)

        return userRepository.save(
            User(
                id = null,
                email = email,
                provider = provider,
                providerId = info.providerId,
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )
    }

    // provider가 email을 못 준 경우를 위한 내부 전용 값. 실제 이메일이 아니므로 발송 용도로 쓰면 안 됨
    private fun placeholderEmail(provider: AuthProvider, providerId: String): String =
        "$providerId@${provider.name.lowercase()}.bali.internal"
}
