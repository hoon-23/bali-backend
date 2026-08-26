# 소셜 로그인에서 백엔드가 client-id를 갖고 있어야 하는 이유

## 배경

2026-08-24, bali-frontend 팀이 Apple/Google/Kakao/Naver 4개 provider 실기기 테스트를 준비하면서 각 provider의 client-id를 백엔드(`application-local.yml`)에 전달했다. 이 과정에서 "client-id를 왜 백엔드가 갖고 있어야 하는지" 질문이 나왔고 그 답을 기록으로 남긴다.

## 결론

Google/Apple/Kakao는 필요하고 Naver는 필요 없다. 이유는 세 provider와 Naver의 인증 검증 방식이 다르기 때문이다.

## OIDC ID Token과 audience 검증

Google/Apple/Kakao는 OIDC(OpenID Connect) 방식을 쓴다. 클라이언트가 로그인하면 provider가 서명된 JWT인 ID Token을 발급하고 클라이언트는 이 토큰을 백엔드로 그대로 전달한다. 백엔드는 이 토큰을 두 단계로 검증한다.

1. **서명 검증**: provider의 JWKS(공개키)로 서명을 확인한다. 통과하면 "이 토큰을 실제로 Google/Apple/Kakao가 발급했다"는 것만 보장된다.
2. **audience 검증**: 토큰의 `aud` 클레임과 우리가 등록한 client-id를 대조한다. 통과하면 "이 토큰이 우리 앱을 대상으로 발급됐다"는 것까지 보장된다.

두 검증을 다 통과해야 안전하다. 서명 검증만 하면 무슨 문제가 생기는지 예를 들면 이렇다. 어떤 사용자가 완전히 다른 제3의 앱에 Google 로그인을 했다고 하자. 그 앱도 Google이 발급한 진짜 ID Token을 받는다. 이 토큰의 서명은 우리 백엔드가 검증해도 당연히 통과한다. Google이 발급한 게 맞으니까. 하지만 이 토큰은 그 제3의 앱을 위해 발급된 것이지 우리 앱을 위한 게 아니다. audience 검증이 없으면, 이 토큰을 그대로 긁어다 우리 백엔드에 보내는 것만으로 로그인이 뚫린다. `aud == client-id`를 대조하는 절차가 이런 재사용(replay)을 막는다.

이 프로젝트에서는 `OidcIdTokenVerifier`(`bali-api/src/main/kotlin/com/bali/api/auth/social/OidcIdTokenVerifier.kt`)가 이 두 단계를 함께 처리하고 `SocialAuthConfig`(같은 패키지)가 provider별 client-id를 `expectedAudiences`로 넘겨준다. Apple만 client-id 대신 앱의 bundle-id가 audience로 쓰이는데, 원리는 같다.

## Naver가 예외인 이유

Naver는 OIDC를 쓰지 않는다. 클라이언트가 발급받는 건 access token뿐이고 백엔드는 이 토큰을 검증하는 대신 이 토큰을 들고 Naver의 "내 정보 조회" API를 직접 호출한다(`NaverTokenVerifier`). API 호출이 성공한다는 것 자체가 "Naver가 발급한 유효한 access token"이라는 걸 증명한다. 검증을 Naver 서버에 위임하는 구조라, 로컬에서 대조할 client-id/aud 클레임이 애초에 없다.

그래서 Naver client-id/secret은 프론트 SDK 초기화(`NaverLogin.initialize`)에만 쓰이고 백엔드 설정에는 반영할 필요가 없다.

## 요약

| Provider | 검증 방식 | 백엔드가 client-id를 알아야 하는가 |
|---|---|---|
| Google | OIDC ID Token, 서명 + audience 검증 | 필요 |
| Apple | OIDC ID Token, 서명 + audience(bundle-id) 검증 | 필요 (bundle-id) |
| Kakao | OIDC ID Token, 서명 + audience 검증 | 필요 |
| Naver | access token으로 provider API 직접 호출, 검증을 provider에 위임 | 불필요 |

인증 방식이 "토큰을 우리가 직접 검증하느냐" 아니면 "provider API 호출로 위임 검증하느냐"에 따라 client-id 필요 여부가 갈린다.
