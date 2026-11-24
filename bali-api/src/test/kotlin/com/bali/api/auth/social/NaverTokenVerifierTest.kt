package com.bali.api.auth.social

import com.bali.core.user.AuthProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NaverTokenVerifierTest {

    @Test
    fun `provider는 NAVER이다`() {
        val verifier = NaverTokenVerifier(NaverUserInfoClient { NaverUserInfoResponse(id = "n1", email = null) })
        assertEquals(AuthProvider.NAVER, verifier.provider)
    }

    @Test
    fun `id와 email을 SocialUserInfo로 변환한다`() {
        val client = NaverUserInfoClient { accessToken ->
            assertEquals("naver-access-token", accessToken)
            NaverUserInfoResponse(id = "naver-user-1", email = "naver@example.com")
        }
        val verifier = NaverTokenVerifier(client)

        val info = verifier.verify("naver-access-token")

        assertEquals("naver-user-1", info.providerId)
        assertEquals("naver@example.com", info.email)
    }

    @Test
    fun `네이버 별명을 SocialUserInfo nickname으로 전달한다`() {
        val verifier = NaverTokenVerifier(NaverUserInfoClient { NaverUserInfoResponse(id = "n1", email = null, nickname = "운동하는곰") })

        assertEquals("운동하는곰", verifier.verify("t").nickname)
    }

    private val userInfoClient = NaverUserInfoClient { accessToken ->
        assertEquals("exchanged-access-token", accessToken)
        NaverUserInfoResponse(id = "naver-user-1", email = "naver@example.com")
    }

    @Test
    fun `인가 코드를 교환한 access token으로 사용자 정보를 조회한다`() {
        val authClient = NaverAuthClient { code, state ->
            assertEquals("the-code", code)
            assertEquals("the-state", state)
            "exchanged-access-token"
        }
        val verifier = NaverTokenVerifier(userInfoClient, authClient)

        val info = verifier.verifyAuthorizationCode("the-code", "the-state")

        assertEquals("naver-user-1", info.providerId)
        assertEquals("naver@example.com", info.email)
    }

    @Test
    fun `인가 코드 교환이 실패하면 그 예외가 그대로 전파된다`() {
        val authClient = NaverAuthClient { _, _ -> throw IllegalArgumentException("코드 교환 실패") }
        val verifier = NaverTokenVerifier(userInfoClient, authClient)

        assertThrows(IllegalArgumentException::class.java) { verifier.verifyAuthorizationCode("c", "s") }
    }

    // 전환 기간이 끝나면 access token 직접 전달 방식(발급 대상 검증 불가)을 설정으로 끈다
    @Test
    fun `legacy 토큰 방식이 꺼져 있으면 verify는 IllegalArgumentException을 던진다`() {
        val verifier = NaverTokenVerifier(userInfoClient, allowLegacyToken = false)

        assertThrows(IllegalArgumentException::class.java) { verifier.verify("exchanged-access-token") }
    }

    @Test
    fun `legacy 토큰 방식이 꺼져 있어도 인가 코드 경로는 동작한다`() {
        val authClient = NaverAuthClient { _, _ -> "exchanged-access-token" }
        val verifier = NaverTokenVerifier(userInfoClient, authClient, allowLegacyToken = false)

        assertEquals("naver-user-1", verifier.verifyAuthorizationCode("c", "s").providerId)
    }

    @Test
    fun `client secret이 설정되지 않은 환경에서는 인가 코드 교환이 설정 오류로 실패한다`() {
        assertThrows(IllegalStateException::class.java) { NoopNaverAuthClient().exchangeCode("c", "s") }
    }
}
