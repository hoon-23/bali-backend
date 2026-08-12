package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// 카카오 OIDC ID Token을 검증하는 SocialTokenVerifier 구현. 서명/발급자/대상/만료 검증은 OidcIdTokenVerifier에 위임.
// email 클레임은 카카오 콘솔의 email 동의 항목 설정과 사용자 동의 여부에 따라 없을 수 있어 nullable로 다룬다
class KakaoTokenVerifier(
    private val oidcIdTokenVerifier: OidcIdTokenVerifier,
) : SocialTokenVerifier {
    override val provider = AuthProvider.KAKAO

    // 검증된 클레임에서 sub(providerId)와 email을 꺼낸다
    override fun verify(token: String): SocialUserInfo {
        val claims = oidcIdTokenVerifier.verify(token)
        val providerId = claims.subject
            ?: throw IllegalArgumentException("토큰에 sub 클레임이 없습니다")
        // claims.claims["email"]은 파싱된 원시 맵 접근이라 체크 예외(ParseException)를 던지지 않는다
        return SocialUserInfo(providerId = providerId, email = claims.claims["email"] as? String)
    }
}
