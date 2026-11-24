package com.bali.api.auth.social

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException

class RestClientNaverAuthClientTest {

    private fun setUp(): Pair<MockRestServiceServer, RestClientNaverAuthClient> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return server to RestClientNaverAuthClient(builder.build(), clientId = "test-client-id", clientSecret = "test-client-secret")
    }

    @Test
    fun `인가 코드를 client 정보와 함께 POST 폼으로 보내고 access token을 돌려준다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andExpect(content().string(containsString("client_id=test-client-id")))
            .andExpect(content().string(containsString("client_secret=test-client-secret")))
            .andExpect(content().string(containsString("code=the-code")))
            .andExpect(content().string(containsString("state=the-state")))
            .andRespond(withSuccess("""{"access_token": "naver-access", "refresh_token": "r", "token_type": "bearer", "expires_in": "3600"}""", MediaType.APPLICATION_JSON))

        assertEquals("naver-access", client.exchangeCode("the-code", "the-state"))
        server.verify()
    }

    // 네이버는 실패를 200 응답의 error 필드로 돌려줄 수 있어, 상태 코드만 보면 성공으로 오인한다
    @Test
    fun `200 응답이어도 error 필드가 있으면 IllegalArgumentException을 던진다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
            .andRespond(withSuccess("""{"error": "invalid_request", "error_description": "no valid data in session"}""", MediaType.APPLICATION_JSON))

        assertThrows(IllegalArgumentException::class.java) { client.exchangeCode("bad-code", "s") }
    }

    @Test
    fun `응답에 access token이 없으면 IllegalArgumentException을 던진다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
            .andRespond(withSuccess("""{"token_type": "bearer"}""", MediaType.APPLICATION_JSON))

        assertThrows(IllegalArgumentException::class.java) { client.exchangeCode("c", "s") }
    }

    @Test
    fun `4xx 응답이면 IllegalArgumentException을 던진다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token")).andRespond(withBadRequest())

        assertThrows(IllegalArgumentException::class.java) { client.exchangeCode("c", "s") }
    }

    @Test
    fun `5xx 응답이면 SocialProviderUnavailableException을 던진다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token")).andRespond(withServerError())

        assertThrows(SocialProviderUnavailableException::class.java) { client.exchangeCode("c", "s") }
    }

    @Test
    fun `연결 실패면 SocialProviderUnavailableException을 던진다`() {
        val (server, client) = setUp()
        server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
            .andRespond { throw IOException("connection refused") }

        assertThrows(SocialProviderUnavailableException::class.java) { client.exchangeCode("c", "s") }
    }
}
