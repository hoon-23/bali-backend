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
        return SocialUserInfo(providerId = providerId, email = claims.getStringClaim("email"))
    }
}
