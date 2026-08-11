package com.bali.api.auth.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class RestClientKakaoUserInfoClientTest {

    @Test
    fun `access token을 Authorization 헤더로 보내고 응답을 파싱한다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(header("Authorization", "Bearer test-access-token"))
            .andRespond(
                withSuccess(
                    """{"id": 123456, "kakao_account": {"email": "kakao@example.com"}}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val client = RestClientKakaoUserInfoClient(restClient)
        val response = client.fetchMe("test-access-token")

        assertEquals(123456L, response.id)
        assertEquals("kakao@example.com", response.email)
        mockServer.verify()
    }

    @Test
    fun `email이 없는 kakao_account 응답도 정상 파싱한다`() {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()

        mockServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(header("Authorization", "Bearer test-access-token"))
            .andRespond(
                withSuccess(
                    """{"id": 654321, "kakao_account": {"email": null}}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val client = RestClientKakaoUserInfoClient(restClient)
        val response = client.fetchMe("test-access-token")

        assertEquals(654321L, response.id)
        assertEquals(null, response.email)
        mockServer.verify()
    }
}
