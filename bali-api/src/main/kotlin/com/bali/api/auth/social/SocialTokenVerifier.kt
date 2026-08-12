package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// provider 토큰을 검증해 신원 정보만 돌려주는 포트. provider별 구현은 검증 방식만 다르다
interface SocialTokenVerifier {
    val provider: AuthProvider

    // 토큰이 유효하지 않으면 IllegalArgumentException을 던진다. REST 기반 구현(Naver)은 provider
    // 장애(5xx/연결 실패)와 토큰 무효를 구분할 수 있는 경우 SocialProviderUnavailableException을
    // 대신 던질 수 있다 - ApiExceptionHandler가 이를 502로 별도 매핑한다
    fun verify(token: String): SocialUserInfo
}

// 검증된 신원 정보. email은 Apple 재로그인, 카카오 비즈 미검수 등으로 비어있을 수 있어 nullable
data class SocialUserInfo(val providerId: String, val email: String?)
