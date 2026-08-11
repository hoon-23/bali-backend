package com.bali.api.auth.social

import com.bali.core.user.AuthProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KakaoTokenVerifierTest {

    @Test
    fun `provider는 KAKAO이다`() {
        val verifier = KakaoTokenVerifier(KakaoUserInfoClient { KakaoUserInfoResponse(id = 1L, email = null) })
        assertEquals(AuthProvider.KAKAO, verifier.provider)
    }

    @Test
    fun `id와 email을 SocialUserInfo로 변환한다`() {
        val client = KakaoUserInfoClient { accessToken ->
            assertEquals("kakao-access-token", accessToken)
            KakaoUserInfoResponse(id = 123456L, email = "kakao@example.com")
        }
        val verifier = KakaoTokenVerifier(client)

        val info = verifier.verify("kakao-access-token")

        assertEquals("123456", info.providerId)
        assertEquals("kakao@example.com", info.email)
    }

    @Test
    fun `email이 없으면 null로 전달된다 (비즈 앱 미검수 상황)`() {
        val verifier = KakaoTokenVerifier(KakaoUserInfoClient { KakaoUserInfoResponse(id = 1L, email = null) })
        assertNull(verifier.verify("token").email)
    }
}
