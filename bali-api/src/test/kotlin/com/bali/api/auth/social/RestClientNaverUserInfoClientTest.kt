package com.bali.api.auth.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.web.client.RestClient

class RestClientNaverUserInfoClientTest {

    @Test
    fun `access token을 Authorization 헤더로 보내고 응답을 파싱한다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andExpect(header("Authorization", "Bearer test-access-token"))
            .andRespond(
                withSuccess(
                    """{"resultcode": "00", "message": "success", "response": {"id": "naver-user-1", "email": "naver@example.com"}}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val client = RestClientNaverUserInfoClient(restClient)
        val response = client.fetchMe("test-access-token")

        assertEquals("naver-user-1", response.id)
        assertEquals("naver@example.com", response.email)
        mockServer.verify()
    }

    @Test
    fun `만료 무효 토큰으로 401을 받으면 IllegalArgumentException을 던진다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andExpect(header("Authorization", "Bearer expired-token"))
            .andRespond(withUnauthorizedRequest())

        val client = RestClientNaverUserInfoClient(restClient)

        assertThrows(IllegalArgumentException::class.java) { client.fetchMe("expired-token") }
        mockServer.verify()
    }
}
