package com.bali.api.legal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

// 실제 필터 체인을 통해 개인정보처리방침 엔드포인트가 인증 없이도(permitAll) 도달 가능한지,
// classpath 리소스가 정상적으로 응답 본문에 포함되는지 확인하는 회귀 테스트
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegalControllerTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `privacy policy endpoint is reachable without a token and returns markdown`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/legal/privacy-policy",
            String::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.headers.contentType.toString().startsWith("text/markdown"))
        assertTrue(response.body!!.contains("개인정보처리방침"))
        assertTrue(response.body!!.contains("Swayt"))
    }
}
