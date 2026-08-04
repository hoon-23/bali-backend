package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.time.Instant

// Google OAuth2 로그인 시 사용자 정보를 조회/생성하는 커스텀 서비스
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {

    // Spring Security의 OAuth2 로그인 플로우에서 호출되어 사용자 정보를 로드
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        // 기본 구현으로 Google 사용자 정보(속성)를 가져옴
        val oAuth2User = super.loadUser(userRequest)
        val providerId = oAuth2User.getAttribute<String>("sub")!!
        val email = oAuth2User.getAttribute<String>("email")!!

        // 사용자 조회/생성 (결과는 성공 핸들러에서 다시 조회하여 사용)
        resolveUser(providerId, email)

        return oAuth2User
    }

    // 프로바이더 ID로 기존 사용자를 조회하거나, 없으면 신규 사용자를 생성해서 저장
    fun resolveUser(providerId: String, email: String): User {
        // 기존에 가입된 사용자인지 먼저 확인
        val existing = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        if (existing != null) return existing

        // 최초 로그인이면 신규 사용자를 생성하여 저장
        return userRepository.save(
            User(
                id = null,
                email = email,
                provider = AuthProvider.GOOGLE,
                providerId = providerId,
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )
    }
}
