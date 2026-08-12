package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// Google ID Token을 검증하는 SocialTokenVerifier 구현. 서명/발급자/대상/만료 검증은 OidcIdTokenVerifier에 위임
class GoogleTokenVerifier(
    private val oidcIdTokenVerifier: OidcIdTokenVerifier,
) : SocialTokenVerifier {
    override val provider = AuthProvider.GOOGLE

    // 검증된 클레임에서 sub(providerId)와 email을 꺼낸다
    override fun verify(token: String): SocialUserInfo {
        val claims = oidcIdTokenVerifier.verify(token)
        val providerId = claims.subject
            ?: throw IllegalArgumentException("토큰에 sub 클레임이 없습니다")
        // getStringClaim("email")은 체크 예외(ParseException)를 던질 수 있어 verify() 계약
        // (IllegalArgumentException만) 이론상 깨질 수 있다. claims.claims["email"]은 파싱된
        // 원시 맵 접근이라 그런 예외를 던지지 않는다
        return SocialUserInfo(providerId = providerId, email = claims.claims["email"] as? String)
    }
}
