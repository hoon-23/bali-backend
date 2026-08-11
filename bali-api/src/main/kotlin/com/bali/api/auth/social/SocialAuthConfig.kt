package com.bali.api.auth.social

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

// Google/Apple 검증기가 쓰는 OidcIdTokenVerifier, Kakao/Naver 검증기가 쓰는 REST 클라이언트를
// 실제 설정값(issuer/audience/JWKS URL)으로 조립해 빈으로 등록
@Configuration
class SocialAuthConfig(
    @Value("\${bali.oauth.google.client-id}") private val googleClientId: String,
    @Value("\${bali.oauth.apple.bundle-id}") private val appleBundleId: String,
) {
    // Kakao/Naver User-Info API 호출과 JWKS fetch가 공유하는 기본 RestClient
    @Bean
    fun socialAuthRestClient(): RestClient = RestClient.create()

    // Google ID Token 검증기: 구글 JWKS를 캐싱해서 서명/발급자/대상을 검증
    @Bean
    fun googleTokenVerifier(socialAuthRestClient: RestClient): GoogleTokenVerifier = GoogleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = CachingJwkSetSupplier("https://www.googleapis.com/oauth2/v3/certs", socialAuthRestClient),
            expectedIssuer = "https://accounts.google.com",
            expectedAudience = googleClientId,
        )
    )

    // Apple identity token 검증기: 애플 JWKS를 캐싱해서 서명/발급자/대상을 검증
    @Bean
    fun appleTokenVerifier(socialAuthRestClient: RestClient): AppleTokenVerifier = AppleTokenVerifier(
        OidcIdTokenVerifier(
            jwkSetSupplier = CachingJwkSetSupplier("https://appleid.apple.com/auth/keys", socialAuthRestClient),
            expectedIssuer = "https://appleid.apple.com",
            expectedAudience = appleBundleId,
        )
    )

    // 카카오 access token 검증기: 카카오 "내 정보 조회" API 호출을 검증 수단으로 사용
    @Bean
    fun kakaoTokenVerifier(socialAuthRestClient: RestClient): KakaoTokenVerifier =
        KakaoTokenVerifier(RestClientKakaoUserInfoClient(socialAuthRestClient))

    // 네이버 access token 검증기: 네이버 "내 정보 조회" API 호출을 검증 수단으로 사용
    @Bean
    fun naverTokenVerifier(socialAuthRestClient: RestClient): NaverTokenVerifier =
        NaverTokenVerifier(RestClientNaverUserInfoClient(socialAuthRestClient))
}
