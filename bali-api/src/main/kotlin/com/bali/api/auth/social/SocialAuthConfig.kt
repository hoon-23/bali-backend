package com.bali.api.auth.social

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

// Google/Apple/Kakao 검증기가 쓰는 OidcIdTokenVerifier, Naver 검증기가 쓰는 REST 클라이언트를
// 실제 설정값(issuer/audience/JWKS URL)으로 조립해 빈으로 등록
@Configuration
class SocialAuthConfig(
    @Value("\${bali.oauth.google.client-id}") private val googleClientId: String,
    @Value("\${bali.oauth.apple.bundle-id}") private val appleBundleId: String,
    @Value("\${bali.oauth.kakao.client-id}") private val kakaoClientId: String,
) {
    // Naver User-Info API 호출과 Google/Apple/Kakao JWKS fetch가 공유하는 기본 RestClient.
    // /api/v1/auth/login은 인증 없이 호출 가능한 엔드포인트라, provider가 느리거나 응답을 안 주면
    // 무제한 대기로 요청 처리 스레드가 고갈될 수 있어 연결/응답 타임아웃을 명시적으로 둔다
    @Bean
    fun socialAuthRestClient(): RestClient {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3))
        )
        return RestClient.builder().requestFactory(requestFactory).build()
    }

    // Google ID Token 검증기: 구글 JWKS를 캐싱해서 서명/발급자/대상을 검증. 구글 문서가 iss를
    // "https://accounts.google.com" 또는 "accounts.google.com" 둘 다 유효하다고 명시하므로 둘 다 허용한다
    @Bean
    fun googleTokenVerifier(socialAuthRestClient: RestClient): GoogleTokenVerifier = GoogleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = CachingJwkSetSupplier("https://www.googleapis.com/oauth2/v3/certs", socialAuthRestClient),
            expectedIssuers = setOf("https://accounts.google.com", "accounts.google.com"),
            expectedAudiences = setOf(googleClientId),
        )
    )

    // Apple identity token 검증기: 애플 JWKS를 캐싱해서 서명/발급자/대상을 검증
    @Bean
    fun appleTokenVerifier(socialAuthRestClient: RestClient): AppleTokenVerifier = AppleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = CachingJwkSetSupplier("https://appleid.apple.com/auth/keys", socialAuthRestClient),
            expectedIssuers = setOf("https://appleid.apple.com"),
            expectedAudiences = setOf(appleBundleId),
        )
    )

    // 카카오 ID Token 검증기: 카카오 JWKS를 캐싱해서 서명/발급자/대상을 검증(OIDC 활성화 + scope=openid 필요)
    @Bean
    fun kakaoTokenVerifier(socialAuthRestClient: RestClient): KakaoTokenVerifier = KakaoTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = CachingJwkSetSupplier("https://kauth.kakao.com/.well-known/jwks.json", socialAuthRestClient),
            expectedIssuers = setOf("https://kauth.kakao.com"),
            expectedAudiences = setOf(kakaoClientId),
        )
    )

    // 네이버 access token 검증기: 네이버 "내 정보 조회" API 호출을 검증 수단으로 사용
    @Bean
    fun naverTokenVerifier(socialAuthRestClient: RestClient): NaverTokenVerifier =
        NaverTokenVerifier(RestClientNaverUserInfoClient(socialAuthRestClient))
}
