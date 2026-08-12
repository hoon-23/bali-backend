package com.bali.api.auth.social

import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

// 네이버 "내 정보 조회" API 호출을 추상화하는 포트. 실전 구현은 REST 호출, 테스트는 fake로 대체
fun interface NaverUserInfoClient {
    fun fetchMe(accessToken: String): NaverUserInfoResponse
}

// 네이버 응답에서 우리가 쓰는 필드만 추출한 값 객체
data class NaverUserInfoResponse(val id: String, val email: String?)

// openapi.naver.com/v1/nid/me 응답 전체 형태의 Jackson 역직렬화 DTO
data class NaverMeApiResponse(val response: NaverProfile?) {
    // response 안에 중첩된 실제 프로필 정보
    data class NaverProfile(val id: String, val email: String?)
}

// RestClient로 네이버 "내 정보 조회" API를 실제로 호출하는 구현
class RestClientNaverUserInfoClient(private val restClient: RestClient) : NaverUserInfoClient {
    override fun fetchMe(accessToken: String): NaverUserInfoResponse {
        // 만료/무효/폐기된 토큰이면 네이버가 4xx를 응답한다 - 이건 진짜 "토큰이 잘못됨"이므로
        // IllegalArgumentException(400)으로 통일한다. 반면 5xx(서버 오류)나 연결/응답 실패
        // (ResourceAccessException, 타임아웃 포함)는 토큰이 아니라 네이버 쪽 장애일 가능성이 높아
        // SocialProviderUnavailableException(502)으로 구분한다 - 4xx/5xx를 뭉뚱그리면 provider
        // 장애를 "당신 토큰이 잘못됐다"로 클라이언트에게 오분류해서 보여주게 된다
        val response = try {
            restClient.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(NaverMeApiResponse::class.java)
                ?.response
        } catch (ex: HttpServerErrorException) {
            throw SocialProviderUnavailableException("네이버 서버 오류로 사용자 정보를 가져오지 못했습니다", ex)
        } catch (ex: ResourceAccessException) {
            throw SocialProviderUnavailableException("네이버에 연결하지 못했습니다", ex)
        } catch (ex: RestClientException) {
            throw IllegalArgumentException("네이버 사용자 정보를 가져오지 못했습니다", ex)
        }
            ?: throw IllegalArgumentException("네이버 사용자 정보를 가져오지 못했습니다")

        return NaverUserInfoResponse(id = response.id, email = response.email)
    }
}
