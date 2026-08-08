package com.bali.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

// local 프로필(기본 활성 프로필)에서 Swagger 문서 경로가 인증 없이 열리는지 확인하는
// 보안 게이팅 회귀 테스트. dev 프로필 케이스는 SwaggerDocsAccessDevProfileTest 참고.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerDocsAccessTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `v3 api-docs is accessible without auth on local profile`() {
        val response = restTemplate.getForEntity("http://localhost:$port/v3/api-docs", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
