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
- **OIDC ID Token(JWT) 방식 — Google, Apple**: provider가 서명한 JWT를 서버가 provider의
  JWKS(공개키)로 직접 서명 검증한다. provider 서버에 매 요청마다 네트워크 호출이 필요 없다
  (키만 캐싱).
- **Access Token + User-Info API 방식 — Kakao, Naver**: 서버가 access token을 들고 provider의
  "내 정보 조회" REST API를 매번 호출해서 신원을 확인한다.

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

**Apple 최초 로그인인데 `email`이 없으면 400으로 거부하는 이유:** iOS의 `Sign in with Apple`
표준 동의 화면에는 "이메일 공유 거부" 옵션이 없다 — 실제 이메일 또는 릴레이(가림) 이메일 중
하나는 항상 온다. 그래서 최초 로그인에 `email`이 비어있다는 건 정상 사용자 시나리오가 아니라
클라이언트 버그이거나, 클라이언트가 Apple로부터 토큰은 받았지만 그 직후 우리 서버 호출이
실패해 계정 생성이 안 된 채로 재시도된 타이밍 문제다(Apple은 한 번 내려준 `email`을 재로그인
시 다시 주지 않으므로 재시도해도 복구 불가). 업계 표준대로 이 경우 계정을 만들지 않고 400으로
거부한다. `User.email`은 계속 non-null로 유지한다.

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
→ 400 Bad Request (토큰 검증 실패, 잘못된 provider 값, Apple 최초 로그인인데 email 누락)
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

// 검증된 신원 정보. email은 Apple 재로그인 시 비어있을 수 있어 nullable
data class SocialUserInfo(val providerId: String, val email: String?)
```

- `GoogleTokenVerifier`, `AppleTokenVerifier` — JWKS 기반 JWT 서명 검증(`iss`/`aud`/`exp` 확인
  후 `sub` 추출). JWKS 조회/캐싱 로직은 두 구현이 공유하는 작은 헬퍼로 뽑는다.
- `KakaoTokenVerifier` — `GET kapi.kakao.com/v2/user/me` 호출, 응답의 `id`/`kakao_account.email`
  파싱.
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

    // 최초 로그인 생성. email은 토큰 안 값(Google/Kakao/Naver) 또는 요청 바디 값(Apple) 순으로 사용
    private fun createUser(provider: AuthProvider, info: SocialUserInfo, requestEmail: String?): User {
        val email = info.email ?: requestEmail
            ?: throw IllegalArgumentException("최초 로그인에는 email이 필요합니다")

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
`ApiExceptionHandler` 자체는 수정하지 않는다. `SocialLoginService.createUser`의 email 누락
예외도 같은 타입이라 동일하게 400으로 처리된다.

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

Google/Apple만 audience 검증용 설정이 필요하다(Kakao/Naver는 access token을 그대로 provider
API에 전달만 하므로 서버 쪽 별도 키가 필요 없다).

```yaml
bali:
  oauth:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
    apple:
      bundle-id: ${APPLE_BUNDLE_ID}
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
  기존 유저 재로그인(같은 id 반환), Apple 최초 로그인 email 누락 시 예외 케이스.
- **`AuthControllerTest`**: 전체 흐름 통합 테스트 — provider별 성공 케이스, 검증 실패 시 400.

## 향후 고려사항

- 카카오 비즈니스 앱 전환/검수 완료 전까지는 `email`이 null로 올 수 있다 — 운영 설정 이슈이며,
  실제로 겪으면 그때 `createUser`의 email 필수 검증을 어떻게 완화할지 다시 판단한다.
- 소셜 provider의 access/id token 자체의 만료·재발급 정책은 이번 범위 밖이다 — 로그인 시점에
  한 번만 검증하고, 이후 인증은 전부 자체 발급 JWT(`JwtTokenProvider`)로 처리하므로 provider
  토큰의 수명은 우리 서비스 세션 길이에 영향을 주지 않는다.
- 개인정보처리방침에 4개 provider 각각의 수집 항목을 명시해야 한다 —
  [[project_pipa_compliance_gaps]] 트랙에서 TestFlight 제출 전 처리.
