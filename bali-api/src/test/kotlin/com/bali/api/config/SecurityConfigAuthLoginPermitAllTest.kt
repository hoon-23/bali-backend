package com.bali.api.config

import org.junit.jupiter.api.Assertions.assertEquals
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
        // Authorization 헤더 없이, JSON 형식은 유효하지만 토큰 값이 잘못된 바디를 보낸다. 컨트롤러/
        // 서비스 계층까지 정상 도달해 ApiExceptionHandler가 직접 400을 응답하는 경로를 탄다 -
        // permitAll이 제대로 동작해 필터 체인을 통과했다면 정확히 400이 와야 한다(401이면 인증 필터에
        // 막힌 것, 500이면 다른 문제)
        val request = HttpEntity("""{"provider":"GOOGLE","token":"invalid-token"}""", headers)
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/auth/login",
            request,
            String::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `unrecognized provider enum value in JSON body still returns 400, not 401`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        // "FACEBOOK"은 AuthProvider enum에 없는 값이라 Jackson 역직렬화가 실패해
        // HttpMessageNotReadableException을 던진다. 이 경로가 ApiExceptionHandler에서 직접
        // 처리되지 않으면 response.sendError -> "/error" 내부 재전송 -> SecurityConfig가 "/error"를
        // permitAll에 안 넣어서 인증 없는 요청이 401로 잘못 가려지는 회귀가 있었다(최종 리뷰에서 발견)
        val request = HttpEntity("""{"provider":"FACEBOOK","token":"some-token"}""", headers)
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/auth/login",
            request,
            String::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
