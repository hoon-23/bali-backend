package com.bali.api.config

import com.bali.api.auth.CustomOAuth2UserService
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ObservationAuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.util.ReflectionTestUtils

// SecurityConfig가 CustomOAuth2UserService를 OIDC 로그인 경로(oidcUserService)에 실제로
// 연결했는지 검증하는 배선(wiring) 회귀 테스트.
// Google의 scope에 openid가 포함되면 Spring Security는 이 프로바이더를 OIDC로 취급해서
// oauth2Login { userInfoEndpoint { userService = ... } }가 아니라 oidcUserService 슬롯을 사용한다.
// 잘못된 슬롯에 연결해도 CustomOAuth2UserService의 단위 테스트는 여전히 통과하기 때문에,
// 실제 필터 체인 내부의 인증 프로바이더가 우리가 등록한 빈을 참조하는지까지 확인해야 이 배선 버그를 잡을 수 있다.
@SpringBootTest
class SecurityConfigOidcWiringTest {

    @Autowired
    private lateinit var securityFilterChain: SecurityFilterChain

    @Autowired
    private lateinit var customOAuth2UserService: CustomOAuth2UserService

    // OAuth2LoginAuthenticationFilter 안의 OidcAuthorizationCodeAuthenticationProvider가
    // 우리가 등록한 CustomOAuth2UserService 빈을 userService로 갖고 있는지 리플렉션으로 확인
    @Test
    fun `oidc authentication provider is wired to CustomOAuth2UserService`() {
        // 필터 체인에서 OAuth2 로그인 처리를 담당하는 필터를 찾는다
        val filter = securityFilterChain.filters
            .filterIsInstance<OAuth2LoginAuthenticationFilter>()
            .single()

        // 필터가 내부적으로 사용하는 AuthenticationManager를 꺼낸다
        // (마이크로미터 옵저버빌리티가 활성화되어 있으면 ObservationAuthenticationManager가 실제 ProviderManager를 감싸므로 한 겹 풀어준다)
        var authenticationManager = ReflectionTestUtils.getField(filter, "authenticationManager") as AuthenticationManager
        if (authenticationManager is ObservationAuthenticationManager) {
            authenticationManager = ReflectionTestUtils.getField(authenticationManager, "delegate") as AuthenticationManager
        }
        val providerManager = authenticationManager as ProviderManager

        // 여러 AuthenticationProvider 중 OIDC 인증을 처리하는 프로바이더를 찾는다
        val oidcProvider = providerManager.providers
            .filterIsInstance<OidcAuthorizationCodeAuthenticationProvider>()
            .single()

        // 해당 프로바이더가 실제로 사용하는 userService가 우리 빈과 동일한 인스턴스인지 검증
        val wiredUserService = ReflectionTestUtils.getField(oidcProvider, "userService")
        assertSame(customOAuth2UserService, wiredUserService)
    }
}
