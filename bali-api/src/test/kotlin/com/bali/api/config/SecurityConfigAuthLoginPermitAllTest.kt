package com.bali.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// 실제 필터 체인을 통해 /api/v1/auth/login이 인증 없이도(permitAll) 도달 가능한지 확인하는
// 보안 게이팅 회귀 테스트. SecurityConfig의 authorizeHttpRequests 매처가 잘못되면
// (예: 오타, 잘못된 경로) 이 테스트가 401로 실패해 잡아낸다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigAuthLoginPermitAllTest {

    @LocalServerPort
    var port: Int = 0

    private val restTemplate = TestRestTemplate()

    // TestRestTemplate의 기본 JDK HttpURLConnection 클라이언트는 POST 요청이 401을 받으면
    // "cannot retry due to server authentication, in streaming mode" 예외를 던지는 알려진 문제가
    // 있다(실제 애플리케이션 동작과 무관한 클라이언트 한정 버그). 이 문제가 없는 java.net.http.HttpClient로
    // 우회한다
    private fun postJson(path: String, body: String): HttpResponse<String> {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

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

    // /api/v1/auth/logout이 permitAll인지 확인하는 회귀 테스트. logout은 존재하지 않는 refresh
    // token에도 204(멱등)를 반환하므로, 실제로 서비스 로직까지 도달했는지를 204로 구분할 수 있다.
    // 만약 SecurityConfig의 permitAll이 깨져 필터에서 막히면 이 요청은 401로 응답한다
    @Test
    fun `auth logout endpoint is not blocked by authentication without a token`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val request = HttpEntity("""{"refreshToken":"no-such-token"}""", headers)
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/auth/logout",
            request,
            String::class.java,
        )
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    // /api/v1/auth/refresh가 permitAll인지 확인하는 회귀 테스트. 존재하지 않는 refresh token은
    // 비즈니스 로직에서도 401을 반환하므로 상태 코드만으로는 permitAll 여부를 구분할 수 없다 -
    // 응답 바디의 에러 메시지로 인증 필터 차단(제네릭 "Unauthorized")과 컨트롤러 도달(구체적 메시지)을 구분한다
    @Test
    fun `auth refresh endpoint reaches the controller without a token, not blocked by the auth filter`() {
        val response = postJson("/api/v1/auth/refresh", """{"refreshToken":"no-such-token"}""")

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode())
        assertTrue(response.body().contains("refresh token"))
    }
}
