package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// Apple identity token을 검증하는 SocialTokenVerifier 구현. identity token에는 email이 안정적으로
// 들어있지 않으므로 항상 null로 반환하고, 최초 로그인 email은 상위 계층(SocialLoginService)이
// 요청 바디 값으로 별도 처리한다
class AppleTokenVerifier(
    private val oidcIdTokenVerifier: OidcIdTokenVerifier,
) : SocialTokenVerifier {
    override val provider = AuthProvider.APPLE

    // 검증된 클레임에서 sub(providerId)만 꺼낸다
    override fun verify(token: String): SocialUserInfo {
        val claims = oidcIdTokenVerifier.verify(token)
        val providerId = claims.subject
            ?: throw IllegalArgumentException("토큰에 sub 클레임이 없습니다")
        return SocialUserInfo(providerId = providerId, email = null)
    }
}
