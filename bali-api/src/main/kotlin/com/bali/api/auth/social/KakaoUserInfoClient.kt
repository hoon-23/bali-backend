package com.bali.api.auth.social

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

// 카카오 "내 정보 조회" API 호출을 추상화하는 포트. 실전 구현은 REST 호출, 테스트는 fake로 대체
fun interface KakaoUserInfoClient {
    fun fetchMe(accessToken: String): KakaoUserInfoResponse
}

// 카카오 응답에서 우리가 쓰는 필드만 추출한 값 객체
data class KakaoUserInfoResponse(val id: Long, val email: String?)

// kapi.kakao.com/v2/user/me 응답 전체 형태의 Jackson 역직렬화 DTO
data class KakaoMeApiResponse(
    val id: Long,
    @param:JsonProperty("kakao_account") val kakaoAccount: KakaoAccount?,
) {
    data class KakaoAccount(val email: String?)
}

// RestClient로 카카오 "내 정보 조회" API를 실제로 호출하는 구현
class RestClientKakaoUserInfoClient(private val restClient: RestClient) : KakaoUserInfoClient {
    override fun fetchMe(accessToken: String): KakaoUserInfoResponse {
        // 만료/무효/폐기된 토큰이면 카카오가 4xx/5xx를 응답한다. RestClient는 이를
        // RestClientException(HttpClientErrorException 등)으로 던지는데, 이를 그대로 흘리면
        // verify() 계약(IllegalArgumentException)을 어기게 되므로 여기서 통일한다
        val response = try {
            restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(KakaoMeApiResponse::class.java)
        } catch (ex: RestClientException) {
            throw IllegalArgumentException("카카오 사용자 정보를 가져오지 못했습니다", ex)
        }
        if (response == null) {
            throw IllegalArgumentException("카카오 사용자 정보를 가져오지 못했습니다")
        }

        return KakaoUserInfoResponse(id = response.id, email = response.kakaoAccount?.email)
    }
}
