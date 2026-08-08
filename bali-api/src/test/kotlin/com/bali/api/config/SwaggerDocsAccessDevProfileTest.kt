package com.bali.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

// dev 프로필에서도 Swagger 문서 경로가 인증 없이 열리는지 확인. application-dev.yml은
// DB_URL/DB_USERNAME/DB_PASSWORD를 fallback 없이 요구하므로, 테스트에서는 로컬 DB 좌표를
// 직접 주입해 프로필 게이팅 로직만 독립적으로 검증한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:5432/bali",
        "spring.datasource.username=bali",
        "spring.datasource.password=bali",
    ]
)
class SwaggerDocsAccessDevProfileTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `v3 api-docs is accessible without auth on dev profile`() {
        val response = restTemplate.getForEntity("http://localhost:$port/v3/api-docs", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
