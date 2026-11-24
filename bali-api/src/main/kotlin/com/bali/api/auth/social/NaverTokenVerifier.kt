package com.bali.api.auth.social

import com.bali.core.user.AuthProvider

// 네이버 신원을 검증하는 SocialTokenVerifier 구현. 두 경로가 있다.
// 1) 인가 코드 경로(verifyAuthorizationCode): 서버가 코드를 client id/secret으로 교환해 우리 앱용 토큰임을 보장한다
// 2) access token 직접 전달 경로(verify): "내 정보 조회" 호출만으로는 토큰의 발급 대상 앱을 검증할 수 없다.
//    이미 배포된 구버전 앱을 위해 전환 기간에만 허용하고, allowLegacyToken=false로 끈다
class NaverTokenVerifier(
    private val client: NaverUserInfoClient,
    private val authClient: NaverAuthClient = NoopNaverAuthClient(),
    private val allowLegacyToken: Boolean = true,
) : SocialTokenVerifier {
    override val provider = AuthProvider.NAVER

    override fun verify(token: String): SocialUserInfo {
        require(allowLegacyToken) { "이 앱 버전의 네이버 로그인은 더 이상 지원되지 않습니다. 앱을 최신 버전으로 업데이트해 주세요" }
        return fetchInfo(token)
    }

    override fun verifyAuthorizationCode(code: String, state: String): SocialUserInfo =
        fetchInfo(authClient.exchangeCode(code, state))

    // access token으로 "내 정보 조회"를 호출해 신원 정보를 돌려준다(실패하면 client가 예외를 던짐)
    private fun fetchInfo(accessToken: String): SocialUserInfo {
        val info = client.fetchMe(accessToken)
        return SocialUserInfo(providerId = info.id, email = info.email, nickname = info.nickname)
    }
}
