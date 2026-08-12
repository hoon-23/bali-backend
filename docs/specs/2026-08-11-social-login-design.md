# 소셜 로그인 확장(네이버/카카오/애플) — 설계 문서

## 배경

`PROGRESS.md` Phase 2 #4. 현재 Google OAuth만 지원하고, 그마저도 서버 주도 브라우저 리다이렉트
(Spring Security `oauth2Login`) 방식이다. 이 프로젝트는 TestFlight 제출을 목표로 하는 모바일
앱이 전제라 이 방식은 애초에 맞지 않는다는 걸 이번 브레인스토밍에서 재확인했다. 네이버/카카오/
애플 3개 provider를 추가하면서, 인증 방식 자체를 모바일 앱 업계 표준인 "클라이언트(모바일)
토큰 검증" 방식으로 전환한다. `[[project_next_step_exercise_catalog]]` Phase 2 순서상 세션
삭제(#2), 개인 종목 수정/삭제(#3) 다음 항목이다.

## 인증 표준 개념 정리

두 가지 흐름이 있다.

**서버 주도 리다이렉트 방식(Authorization Code Grant, 웹 표준, 기존 Google 방식)**: 브라우저가
`/oauth2/authorization/google`로 이동 → provider 로그인 화면 → provider가 우리 서버로
리다이렉트(코드 전달) → 서버가 코드를 access token으로 교환하고 유저 정보를 조회한다. 모바일
앱에서 쓰려면 웹뷰가 필요해 UX가 나쁘고, Apple은 이 방식을 앱 심사에서 사실상 거부한다.

**클라이언트 토큰 검증 방식(업계 표준, 이번에 채택)**: 앱이 provider 네이티브 SDK로 직접
로그인 → SDK가 토큰을 앱에 돌려줌 → 앱이 그 토큰을 우리 서버로 전달 → 서버가 토큰이 진짜
그 provider가 발급한 게 맞는지 검증하고, 검증된 정보로 유저를 찾거나 생성한다. 검증 방법이
provider마다 두 갈래로 나뉜다.
- **OIDC ID Token(JWT) 방식 — Google, Apple, Kakao**: provider가 서명한 JWT를 서버가 provider의
  JWKS(공개키)로 직접 서명 검증한다. provider 서버에 매 요청마다 네트워크 호출이 필요 없다
  (키만 캐싱). 카카오는 콘솔에서 OpenID Connect를 활성화하고 클라이언트가 `scope=openid`로
  토큰을 요청하면 `iss=https://kauth.kakao.com`, `aud=<카카오 REST API 키>`, `sub=<회원번호>`인
  ID Token을 정식으로 발급한다(최종 리뷰에서 REST API 방식 대신 이 방식으로 전환 결정, 최초
  설계 시점에는 카카오 OIDC 지원 여부를 확인하지 못해 Access Token 방식으로 시작했었다).
- **Access Token + User-Info API 방식 — Naver**: 서버가 access token을 들고 provider의
  "내 정보 조회" REST API를 매번 호출해서 신원을 확인한다. 네이버는 OIDC/토큰 검증 API를 제공하지
  않아 이 방식 외에 대안이 없다("향후 고려사항"의 감수 리스크 참고).

## 범위

### 이번 작업
- `POST /api/v1/auth/login` 신규 엔드포인트, 4개 provider(GOOGLE/NAVER/KAKAO/APPLE) 지원
- 기존 Google OAuth2 브라우저 리다이렉트 코드(`SecurityConfig`의 `oauth2Login`,
  `CustomOAuth2UserService`, `OAuth2LoginSuccessHandler`) 전량 제거, Google도 토큰 검증
  방식으로 통일
- `AuthProvider` enum 확장 + `users` 테이블 `CHECK` 제약 마이그레이션

### 범위 밖
- `bali-frontend` 클라이언트 구현 — 아직 코드가 없어(메모리 폴더만 존재) 이번 백엔드 설계
  대상이 아니다. 이번 Phase 2 완료 후 별도 세션에서 진행([[project_next_step_exercise_catalog]]
  참고).
- 카카오 비즈니스 앱 전환/검수(email 스코프 정식 발급) — 카카오 콘솔 설정이지 코드 설계가
  아니다. "향후 고려사항"에 리스크로만 기록한다.
- 개인정보처리방침 작성, 국외 이전 고지 — 별도 트랙([[project_pipa_compliance_gaps]]),
  TestFlight 제출 전 한 번에 정리 예정이며 이번 작업의 블로커가 아니다.

## 설계 결정

**엔드포인트를 provider별로 나누지 않고 단일 `POST /api/v1/auth/login`으로 통일한 이유:**
provider는 URL 경로가 아니라 요청 바디의 필드로 받는다. 클라이언트 입장에서 로그인 엔드포인트가
하나로 고정되고, provider 분기는 바디 값 하나만 바뀌면 되어 클라이언트 코드가 더 단순해진다.

**Apple `email`을 요청 바디의 선택 필드로 받는 이유:** Apple의 identity token(JWT)에는 `sub`
(유저 고유 ID)만 들어있고 `email`은 없다. iOS 클라이언트 SDK가 해당 유저의 최초 1회 로그인
시에만 `email`을 별도로 내려주기 때문에, 서버는 이 값을 토큰이 아니라 요청 바디로 받아야 한다.
다른 provider는 토큰(Google) 또는 User-Info API 응답(Kakao/Naver)에 `email`이 항상 포함되므로
이 필드를 쓰지 않는다.

**최초 로그인인데 `email`을 못 얻으면 placeholder 이메일로 가입을 허용하는 이유:** 처음에는
Apple처럼 email이 정상적으로 안 오는 상황(iOS 클라이언트 버그, 또는 Apple 토큰은 받았지만
직후 서버 호출이 실패해 계정 생성이 안 된 채로 재시도되는 타이밍 문제 — Apple은 한 번 내려준
`email`을 재로그인 시 다시 주지 않으므로 재시도로 복구 불가)을 400으로 거부하는 방향을
검토했으나, 다음 이유로 방향을 바꿨다: 400 거부는 그 상황을 겪은 유저를 **그 Apple 계정으로
영구히 가입 불가능하게 만든다**(Apple 정책상 서버가 나중에 손쓸 방법이 없음). 반면 placeholder
허용의 단점("나중에 email에 실제 기능을 붙이면 이 유저들만 비어있다")은, 그 시점에 클라이언트가
"이메일을 등록해주세요" 플로우를 한 번 태우면 되는 **나중에 고칠 수 있는 문제**다. 지금 `email`
컬럼은 실제 통신 용도로 쓰이는 곳이 전혀 없고(`UserResponse` 표시, JWT claim 용도뿐 —
`bali-api/.../user/UserResponse.kt:15`, `JwtTokenProvider` 호출부 참고) 표시/식별 용도뿐이라,
지금 당장 감수할 단점이 적다. 그래서 영구히 못 고치는 문제보다 나중에 고칠 수 있는 문제를
택한다.

이 fallback은 Apple 전용이 아니라 `createUser`에 provider 공통으로 둔다 — 카카오도 비즈니스
앱 전환/검수 전에는 `kakao_account.email`이 계속 null일 수 있는데(아래 "향후 고려사항" 참고),
같은 fallback이 그 경우도 자연히 커버한다. `User.email`은 계속 non-null 컬럼으로 유지하되,
"항상 진짜 연락 가능한 이메일"이라는 보장은 이번 결정으로 깨진다는 걸 명시적으로 인지한다.

**기존 Google 리다이렉트 코드를 전량 제거하고 Google도 토큰 검증 방식으로 통일하는 이유:** 이
프로젝트는 웹 사용처가 없고 모바일 앱만 쓸 예정이라 리다이렉트 방식을 남겨둘 이유가 없다. 4개
provider를 같은 방식으로 통일하면 `AuthController`/`SocialLoginService`가 provider 종류와
무관하게 동일한 흐름(검증 → 유저 조회/생성 → JWT 발급)을 타므로 코드가 더 단순해진다.

## API 설계

```
POST /api/v1/auth/login
Body: {
  "provider": "GOOGLE" | "NAVER" | "KAKAO" | "APPLE",
  "token": string,       // Google/Apple: ID/identity token(JWT). Kakao/Naver: access token
  "email": string?       // Apple 최초 로그인에만 클라이언트가 채움. 다른 provider는 무시됨
}
→ 200 OK { "accessToken": "<JWT>" }
→ 400 Bad Request (토큰 검증 실패, 잘못된 provider 값)
```

인증이 필요 없는 로그인 엔드포인트이므로 `SecurityConfig`에서 `/api/v1/auth/login`을
`permitAll`로 추가하고(기존 `/oauth2/**`, `/login/oauth2/**` permitAll 규칙은 제거), 그 외
모든 `/api/v1/**` 경로는 기존과 동일하게 `authenticated`를 유지한다.

## 컴포넌트 설계

```kotlin
// provider 토큰을 검증해 신원 정보만 돌려주는 포트. provider별 구현은 검증 방식만 다르다
interface SocialTokenVerifier {
    val provider: AuthProvider
    fun verify(token: String): SocialUserInfo
}

// 검증된 신원 정보. email은 Apple 재로그인, 카카오 비즈 미검수 등으로 비어있을 수 있어 nullable
data class SocialUserInfo(val providerId: String, val email: String?)
```

- `GoogleTokenVerifier`, `AppleTokenVerifier`, `KakaoTokenVerifier` — JWKS 기반 JWT 서명 검증
  (`iss`/`aud`/`exp` 확인 후 `sub` 추출). JWKS 조회/캐싱 로직은 세 구현이 공유하는 작은 헬퍼
  (`OidcIdTokenVerifier`/`CachingJwkSetSupplier`)로 뽑는다. 카카오는 JWKS URL
  `https://kauth.kakao.com/.well-known/jwks.json`, `email` 클레임은 동의 항목 설정에 따라
  없을 수 있어 Google과 동일하게 nullable로 다룬다.
- `NaverTokenVerifier` — `GET openapi.naver.com/v1/nid/me` 호출, 응답의 `response.id`/
  `response.email` 파싱.

```kotlin
// provider에 맞는 검증기를 골라 신원을 확인하고, 유저 조회/생성 + JWT 발급까지 담당
@Service
class SocialLoginService(
    private val verifiers: List<SocialTokenVerifier>,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun login(provider: AuthProvider, token: String, email: String?): String {
        val verifier = verifiers.first { it.provider == provider }
        val info = verifier.verify(token)

        val user = userRepository.findByProviderAndProviderId(provider, info.providerId)
            ?: createUser(provider, info, email)

        return jwtTokenProvider.generateToken(user.id!!, user.email)
    }

    // 최초 로그인 생성. email 우선순위: 토큰/API 응답값 > 요청 바디 값(Apple 최초 로그인) > placeholder
    private fun createUser(provider: AuthProvider, info: SocialUserInfo, requestEmail: String?): User {
        val email = info.email ?: requestEmail ?: placeholderEmail(provider, info.providerId)

        return userRepository.save(
            User(
                id = null,
                email = email,
                provider = provider,
                providerId = info.providerId,
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        )
    }

    // provider가 email을 못 준 경우를 위한 내부 전용 값. 실제 이메일이 아니므로 발송 용도로 쓰면 안 됨
    private fun placeholderEmail(provider: AuthProvider, providerId: String): String =
        "$providerId@${provider.name.lowercase()}.bali.internal"
}
```

```kotlin
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val socialLoginService: SocialLoginService) {

    // provider 토큰을 검증해 로그인/가입 처리 후 자체 JWT 발급
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: SocialLoginRequest): ResponseEntity<Map<String, String>> {
        val accessToken = socialLoginService.login(request.provider, request.token, request.email)
        return ResponseEntity.ok(mapOf("accessToken" to accessToken))
    }
}

data class SocialLoginRequest(
    val provider: AuthProvider,
    @field:NotBlank val token: String,
    val email: String? = null,
)
```

`SocialTokenVerifier.verify`는 검증 실패 시 `IllegalArgumentException`을 던지고, 기존
`ApiExceptionHandler`(`bali-api/.../common/ApiExceptionHandler.kt`)가 이를 400으로 매핑한다 —
`Exercise` 도메인 검증 실패 등과 동일한 전역 예외 처리를 그대로 재사용하는 것이며, 이번 작업에서
`ApiExceptionHandler` 자체는 수정하지 않는다. `createUser`는 email을 못 얻어도 placeholder로
대체해 항상 성공하므로 이 경로에서 별도로 400을 낼 일은 없다.

`JwtTokenProvider`/`JwtAuthenticationFilter`는 provider와 완전히 무관하므로 변경 없음.

## 도메인/DB 변경

```kotlin
// bali-core: AuthProvider.kt
enum class AuthProvider {
    GOOGLE, NAVER, KAKAO, APPLE,
}
```

```sql
-- bali-infra: V8__expand_auth_provider.sql
ALTER TABLE users DROP CONSTRAINT chk_users_provider;
ALTER TABLE users ADD CONSTRAINT chk_users_provider
    CHECK (provider IN ('GOOGLE', 'NAVER', 'KAKAO', 'APPLE'));
```

`UserJpaEntity.provider`의 기본값(`AuthProvider.GOOGLE`)은 그대로 둔다 — JPA 엔티티 기본
생성자용 placeholder일 뿐 실제 저장 시 항상 명시적으로 채워지므로 의미 없는 값이다.

## 설정값

Google/Apple/Kakao는 OIDC ID Token의 audience 검증용 설정이 필요하다(Naver는 access token을
그대로 provider API에 전달만 하므로 서버 쪽 별도 키가 필요 없다).

```yaml
bali:
  oauth:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
    apple:
      bundle-id: ${APPLE_BUNDLE_ID}
    kakao:
      client-id: ${KAKAO_CLIENT_ID}   # 카카오 REST API 키, ID Token의 aud와 대조
```

기존 `application.yml`의 `spring.security.oauth2.client.registration.google` 블록은 제거한다.

## 기존 코드 제거 범위

- `SecurityConfig`의 `oauth2Login { }` 블록, `/oauth2/**`·`/login/oauth2/**` permitAll 규칙
- `CustomOAuth2UserService.kt`, `OAuth2LoginSuccessHandler.kt` (`bali-api/.../auth/oauth2/`
  패키지 전체)
- `application.yml`의 `spring.security.oauth2.client.*`
- `spring-boot-starter-oauth2-client` 의존성 (`spring-boot-starter-security`는 JWT 필터체인에
  계속 필요하므로 유지)
- 기존 `CustomOAuth2UserServiceTest`, `OAuth2LoginSuccessHandlerTest`,
  `SecurityConfigOidcWiringTest`도 함께 제거(대체하는 `SocialLoginServiceTest` 등으로 커버)

## 테스트 전략

기존 관례(Mockito 없이 직접 fake 구현)를 그대로 따른다.

- **JWT 기반 검증기(Google/Apple)**: 테스트용 키 쌍으로 직접 서명한 JWT를 만들고, JWKS 조회
  부분만 작은 인터페이스로 분리해 fake로 주입 — 실제 네트워크 호출 없이 서명 검증 로직 자체를
  테스트한다. 잘못된 서명/만료/audience 불일치 케이스 포함.
- **REST 기반 검증기(Kakao/Naver)**: User-Info API 호출 부분을 작은 인터페이스로 분리해 fake
  응답을 주입. 정상 응답, email 누락 응답(카카오 비즈 미검수 상황 시뮬레이션) 케이스 포함.
- **`SocialLoginServiceTest`**: 기존 `InMemoryUserRepository` 패턴 재사용. 신규 유저 생성,
  기존 유저 재로그인(같은 id 반환), email을 못 얻은 최초 로그인에 placeholder 이메일로 생성되는
  케이스(`{providerId}@{provider}.bali.internal` 형태 검증).
- **`AuthControllerTest`**: 전체 흐름 통합 테스트 — provider별 성공 케이스, 검증 실패 시 400.

## 향후 고려사항

- **[알려진 리스크, 미해결] 네이버 access token의 앱 오디언스 검증 불가**: 네이버는 카카오와
  달리 OIDC ID Token도, 토큰 introspection API도 제공하지 않는다. 그 결과 `NaverTokenVerifier`가
  클라이언트가 보낸 access token으로 "내 정보 조회" API(`GET openapi.naver.com/v1/nid/me`)를
  호출해 200이 오면 유효하다고 판단하는데, 이 응답은 그 토큰이 *우리 앱 앞으로* 발급됐다는 것을
  증명하지 못한다 — 임의의 다른 네이버 앱용으로 발급된 access token이어도 200이 온다. 즉 다른
  앱에서 네이버 로그인한 사용자가 그 access token을 우리 서버로 흘려보내면 우리 계정으로
  로그인/가입이 가능하다(계정 탈취는 아니지만 앱 신원 위장). 근본적으로 고치려면 서버 주도
  authorization code exchange(클라이언트가 code만 넘기고 서버가 client_secret으로 토큰 교환)로
  전환해야 하는데, 이는 클라이언트 로그인 플로우 자체를 바꾸는 범위라 `bali-frontend` 작업을
  시작할 때 별도로 설계하기로 최종 리뷰에서 의도적으로 보류했다. **TestFlight/실 사용자 데이터가
  걸리기 전에 반드시 재검토해야 한다.**
- 카카오 비즈니스 앱 전환/검수 완료 전까지는 `email`이 null로 올 수 있는데, `createUser`의
  placeholder fallback이 이 경우도 그대로 커버하므로 가입 자체가 막히지는 않는다.
- **가드레일**: 나중에 이메일 발송 기능(리포트, 캠페인 등)을 붙일 때는 반드시 발송 대상에서
  placeholder 도메인(`*.bali.internal`)을 걸러내야 한다 — 실제 수신자가 없는 주소이므로 발송
  시도 자체가 무의미하다. 그 시점에 해당 유저에게 실제 이메일을 등록받는 플로우를 클라이언트에
  추가하는 것도 함께 고려한다.
- 소셜 provider의 access/id token 자체의 만료·재발급 정책은 이번 범위 밖이다 — 로그인 시점에
  한 번만 검증하고, 이후 인증은 전부 자체 발급 JWT(`JwtTokenProvider`)로 처리하므로 provider
  토큰의 수명은 우리 서비스 세션 길이에 영향을 주지 않는다.
- 개인정보처리방침에 4개 provider 각각의 수집 항목을 명시해야 한다 —
  [[project_pipa_compliance_gaps]] 트랙에서 TestFlight 제출 전 처리.
