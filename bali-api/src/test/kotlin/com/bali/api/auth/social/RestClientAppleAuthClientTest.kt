package com.bali.api.auth.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class RestClientAppleAuthClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val pem = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().let {
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(it.private.encoded) + "\n-----END PRIVATE KEY-----"
    }
    private val client = RestClientAppleAuthClient(
        builder.build(), AppleClientSecretGenerator("TEAM", "KEY", pem, "com.younghoon.swayt"), "com.younghoon.swayt",
    )

    @Test
    fun `authorizationCode를 교환해 refresh token을 돌려준다`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("code=the-code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=com.younghoon.swayt")))
            .andRespond(withSuccess("""{"access_token":"a","refresh_token":"apple-refresh","id_token":"i"}""", MediaType.APPLICATION_JSON))

        assertEquals("apple-refresh", client.exchangeCode("the-code"))
        server.verify()
    }

    @Test
    fun `교환이 실패하면 null을 돌려주고 예외를 던지지 않는다`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andRespond(withServerError())

        assertNull(client.exchangeCode("the-code"))
    }

    @Test
    fun `revoke는 refresh_token 힌트와 함께 호출하고 성공하면 true`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=apple-refresh")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token_type_hint=refresh_token")))
            .andRespond(withSuccess())

        assertTrue(client.revoke("apple-refresh"))
        server.verify()
    }

    @Test
    fun `revoke가 실패하면 false`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke")).andRespond(withServerError())

        assertFalse(client.revoke("apple-refresh"))
    }
}
