package com.bali.api.auth.social

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

// 네이버 토큰 엔드포인트 호출을 추상화하는 포트. 인가 코드를 access token으로 교환한다.
// 네이버는 토큰이 어느 앱용인지 알려 주지 않지만, 인가 코드는 발급받은 앱의 client id/secret으로만 교환되므로
// 교환에 성공한 토큰은 우리 앱용이라는 증명이 된다
fun interface NaverAuthClient {
    // 코드 교환에 실패하면 IllegalArgumentException, 네이버 장애면 SocialProviderUnavailableException
    fun exchangeCode(code: String, state: String): String
}

// client id/secret이 설정되지 않은 환경(local/test)에서 쓰는 구현. 서버 설정 오류이므로 IllegalStateException(500)이다
class NoopNaverAuthClient : NaverAuthClient {
    override fun exchangeCode(code: String, state: String): String =
        throw IllegalStateException("네이버 인가 코드 로그인이 설정되지 않았습니다 (bali.oauth.naver.client-id/client-secret)")
}

// 네이버 토큰 응답. 실패해도 200으로 error 필드를 담아 보낼 수 있어 두 경우를 모두 받는다
data class NaverTokenResponse(
    @param:JsonProperty("access_token") val accessToken: String? = null,
    val error: String? = null,
    @param:JsonProperty("error_description") val errorDescription: String? = null,
)

// RestClient로 네이버 토큰 엔드포인트를 POST 폼으로 호출하는 구현. secret이 URL(로그)에 남지 않도록 쿼리가 아니라 본문으로 보낸다
class RestClientNaverAuthClient(
    private val restClient: RestClient,
    private val clientId: String,
    private val clientSecret: String,
) : NaverAuthClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun exchangeCode(code: String, state: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("code", code)
            add("state", state)
        }
        // 4xx는 코드가 무효/만료/재사용된 경우라 IllegalArgumentException(400)으로, 5xx와 연결 실패는 네이버 쪽 장애라
        // SocialProviderUnavailableException(502)으로 구분한다 (NaverUserInfoClient와 같은 기준)
        val response = try {
            restClient.post()
                .uri("https://nid.naver.com/oauth2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(NaverTokenResponse::class.java)
        } catch (ex: HttpServerErrorException) {
            throw SocialProviderUnavailableException("네이버 서버 오류로 인가 코드를 교환하지 못했습니다", ex)
        } catch (ex: ResourceAccessException) {
            throw SocialProviderUnavailableException("네이버에 연결하지 못했습니다", ex)
        } catch (ex: RestClientException) {
            throw IllegalArgumentException("네이버 인가 코드를 교환하지 못했습니다", ex)
        }

        if (response == null || response.error != null || response.accessToken.isNullOrBlank()) {
            // 코드와 secret은 남기지 않고 네이버가 알려 준 오류 사유만 기록한다
            log.warn("네이버 인가 코드 교환 실패: error={}, description={}", response?.error, response?.errorDescription)
            throw IllegalArgumentException("네이버 인가 코드를 교환하지 못했습니다")
        }
        return response.accessToken
    }
}
