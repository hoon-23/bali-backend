package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// 카카오 access token을 검증하는 SocialTokenVerifier 구현. 서명 검증이 아니라
// "내 정보 조회" API 호출 자체가 검증 역할을 한다(실패하면 client가 예외를 던짐)
class KakaoTokenVerifier(
    private val client: KakaoUserInfoClient,
) : SocialTokenVerifier {
    override val provider = AuthProvider.KAKAO

    override fun verify(token: String): SocialUserInfo {
        val info = client.fetchMe(token)
        return SocialUserInfo(providerId = info.id.toString(), email = info.email)
    }
}
