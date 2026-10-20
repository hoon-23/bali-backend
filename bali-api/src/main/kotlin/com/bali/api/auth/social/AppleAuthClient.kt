package com.bali.api.auth.social

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

// Apple 토큰 엔드포인트 호출 포트. 키가 설정되지 않은 환경(local/test)에서는 Noop 구현이 쓰인다
interface AppleAuthClient {
    // 로그인 시 받은 authorizationCode를 Apple refresh token으로 교환한다. 실패하면 null
    fun exchangeCode(authorizationCode: String): String?

    // Apple refresh token을 revoke해 앱과 Apple ID의 연동을 끊는다. 성공하면 true
    fun revoke(refreshToken: String): Boolean
}

// 키가 없을 때 쓰는 구현: 아무 것도 호출하지 않는다
class NoopAppleAuthClient : AppleAuthClient {
    override fun exchangeCode(authorizationCode: String): String? = null
    override fun revoke(refreshToken: String): Boolean = false
}

// RestClient로 Apple /auth/token, /auth/revoke를 호출하는 구현
class RestClientAppleAuthClient(
    private val restClient: RestClient,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val clientId: String,
) : AppleAuthClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun exchangeCode(authorizationCode: String): String? = try {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecretGenerator.generate())
            add("code", authorizationCode)
            add("grant_type", "authorization_code")
        }
        restClient.post().uri("$BASE/auth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse::class.java)
            ?.refreshToken
    } catch (ex: Exception) {
        log.warn("Apple authorizationCode 교환 실패: {}", ex.message)
        null
    }

    override fun revoke(refreshToken: String): Boolean = try {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecretGenerator.generate())
            add("token", refreshToken)
            add("token_type_hint", "refresh_token")
        }
        restClient.post().uri("$BASE/auth/revoke")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity()
        true
    } catch (ex: Exception) {
        log.error("Apple 토큰 revoke 실패: {}", ex.message)
        false
    }

    // Apple 토큰 응답에서 refresh_token만 쓴다
    private data class TokenResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") val refreshToken: String? = null,
    )

    private companion object {
        const val BASE = "https://appleid.apple.com"
    }
}
