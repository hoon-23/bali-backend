package com.bali.api.auth.social

import com.bali.core.user.AuthProvider
import org.junit.jupiter.api.Assertions.assertEquals
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
}
