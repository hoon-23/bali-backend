package com.bali.api.auth.oauth2

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import java.time.Instant

// Google OAuth2 로그인 시 사용자 정보를 조회/생성하는 커스텀 서비스
// Google의 scope에 openid가 포함되어 있어 Spring Security는 이 프로바이더를 OIDC로 취급하므로
// DefaultOAuth2UserService가 아닌 OidcUserService를 상속해야 실제 로그인 시 loadUser가 호출됨
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : OidcUserService() {

    // Spring Security의 OIDC 로그인 플로우에서 호출되어 사용자 정보를 로드
    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        // 기본 구현으로 Google 사용자 정보(ID 토큰/UserInfo 속성)를 가져옴
        val oidcUser = super.loadUser(userRequest)
        val providerId = oidcUser.getAttribute<String>("sub")!!
        val email = oidcUser.getAttribute<String>("email")!!

        // 사용자 조회/생성 (결과는 성공 핸들러에서 다시 조회하여 사용)
        resolveUser(providerId, email)

        return oidcUser
    }

    // 프로바이더 ID로 기존 사용자를 조회하거나, 없으면 신규 사용자를 생성해서 저장
    // 주의: 탈퇴(WITHDRAWN) 사용자도 재로그인 시 그대로 반환됨 - 재활성화 정책은 이번 태스크 범위 밖
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
