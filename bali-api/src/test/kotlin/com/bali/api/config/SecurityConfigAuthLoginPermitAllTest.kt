package com.bali.api.config

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

// 실제 필터 체인을 통해 /api/v1/auth/login이 인증 없이도(permitAll) 도달 가능한지 확인하는
// 보안 게이팅 회귀 테스트. SecurityConfig의 authorizeHttpRequests 매처가 잘못되면
// (예: 오타, 잘못된 경로) 이 테스트가 401로 실패해 잡아낸다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigAuthLoginPermitAllTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `auth login endpoint is not blocked by authentication without a token`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        // Authorization 헤더 없이, JSON 형식은 유효하지만 토큰 값이 잘못된 바디를 보낸다.
        // 형식 자체가 깨진 바디(JSON 파싱 실패)는 HttpMessageNotReadableException ->
        // response.sendError -> "/error"로의 서블릿 내부 재전송을 유발하는데, "/error" 경로는
        // permitAll 대상이 아니라서 인증 없이 재평가되어 401로 가려지는 별개의 문제가 있다
        // (이번 테스트가 검증하려는 permitAll 여부와 무관한 함정이라 회피). 여기서는 컨트롤러/
        // 서비스 계층까지 정상 도달해 ApiExceptionHandler가 직접 400을 응답하는 경로를 탄다 -
        // 400이 오면 permitAll이 제대로 동작해 필터 체인을 통과했다는 뜻이고, 401만 아니면 된다
        val request = HttpEntity("""{"provider":"GOOGLE","token":"invalid-token"}""", headers)
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/auth/login",
            request,
            String::class.java,
        )
        assertNotEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
