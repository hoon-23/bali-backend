# Swagger/OpenAPI 도입 + Spring Profile 분리 — 설계 문서

## 배경

`bali-api`에 REST 컨트롤러가 5개(17개 엔드포인트) 쌓였지만 API 문서화 도구가 전혀 없다.
`[[project_batch_and_api_docs_tooling]]`, `[[project_next_step_exercise_catalog]]`에서 이미
"Swagger 도입"을 Phase 2 항목 중 가장 작고 독립적인 작업으로 확정했다. 작업을 진행하며
Spring Security(`SecurityConfig`)가 기본적으로 `anyRequest().authenticated()`라 Swagger UI
경로도 명시적으로 열어줘야 한다는 게 드러났고, 이 프로젝트에 Spring Profile 개념 자체가 아직
없다는 것도 확인되어 이번 작업 범위에 `local`/`dev`/`prod` profile 분리를 포함시켰다.

`dev`는 현재 사실상 상용 트래픽을 받는 스테이징 환경으로 운영할 예정이며, 실제 스토어 출시
시점의 별도 운영 환경(`prod`)은 아직 필요하지 않다 — 그래서 `prod`는 이번엔 구조만 만들어두는
껍데기로 취급한다.

## 범위

### 이번 작업
- Spring Profile 3종(`local`/`dev`/`prod`) 분리, 기본 활성 프로필은 `local`
- `springdoc-openapi` 도입, JWT Bearer 인증 지원(Authorize 버튼)
- Swagger UI/OpenAPI 문서 경로는 `local`/`dev`에서만 인증 없이 열람 가능, `prod`는 계속 인증 필요
- 컨트롤러 5개(17개 엔드포인트) 전체에 `@Tag`/`@Operation` 한글 설명 추가

### 범위 밖
- `@ApiResponses`로 에러코드(400/401/403/404) 매트릭스까지 문서화하는 것 — summary/description
  수준으로 제한
- `application-prod.yml`의 실제 값 채우기 — 스토어 출시 시점에 별도 작업
- 배포 자동화(Dockerfile/CI, `SPRING_PROFILES_ACTIVE` 실제 서버 주입) — 이 저장소엔 아직 관련
  설정이 전혀 없고, 이번 작업 범위도 아님

## Spring Profile 분리

```
application.yml          # 공통: spring.application.name, JPA, JWT, Google OAuth2, actuator
                          # spring.profiles.active: local (기본값)
application-local.yml    # datasource: localhost:5432/bali (기존 하드코딩 값 그대로 이전)
application-dev.yml      # datasource: ${DB_URL}/${DB_USERNAME}/${DB_PASSWORD} (fallback 없음,
                          # 값 없으면 기동 실패 — 기존 GOOGLE_CLIENT_ID/SECRET과 동일 패턴)
application-prod.yml     # dev와 동일 구조의 껍데기 + 상단 TODO 주석
                          # (스토어 출시 시점에 실값으로 분리)
```

JWT secret/Google OAuth 클라이언트 설정은 이미 환경변수로 외부화되어 있어 프로필별로 나눌
필요가 없다 (공통 유지). datasource만 프로필별로 갈라지는 부분이다.

## Swagger/OpenAPI 구성

- 의존성: `io.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` (Spring Boot 3.3.4 호환)
- `bali-api/.../config/OpenApiConfig.kt` (신규): `OpenAPI` 빈 하나
  - `Info`: title "Bali API", 설명 한 줄
  - `SecurityScheme("bearerAuth")`: HTTP bearer, `bearerFormat = "JWT"`
  - 전역 `SecurityRequirement("bearerAuth")` — 문서화 대상 엔드포인트가 전부 인증 필수라
    엔드포인트별 개별 지정 없이 전역 하나로 처리
- `SecurityConfig.kt`: `Environment`를 생성자로 주입받아, 활성 프로필이 `local` 또는 `dev`일
  때만 Swagger 경로를 permitAll:
  ```kotlin
  if (environment.activeProfiles.any { it in setOf("local", "dev") }) {
      authorize("/swagger-ui/**", permitAll)
      authorize("/v3/api-docs/**", permitAll)
  }
  ```
  `prod`는 별도 분기 없이 기존 `anyRequest().authenticated()`로 자동 차단된다.

## 컨트롤러 문서화

5개 컨트롤러 클래스에 `@Tag(name, description)`, 17개 메서드 전체에 `@Operation(summary,
description)`을 한글로 추가한다 (기존 코드 주석 컨벤션과 동일하게 한 줄 설명 수준 유지).

| 컨트롤러 | 엔드포인트 수 |
|---|---|
| ExerciseController | 3 |
| WeeklyAnalysisController | 2 |
| TemplateController | 5 |
| UserController | 2 |
| SessionController | 5 |

## 테스트 전략

- `local`/`dev` 프로필에서 `/v3/api-docs`가 인증 없이 200을 반환하는지 확인하는 테스트 1개
  (보안 게이팅 로직이 이 프로젝트에서 가장 실수하기 쉬운 지점이므로 자동화)
- 수동 검증: `local` 프로필로 서버 기동 → `/swagger-ui/index.html` 접속 → 태그별 그룹핑,
  한글 설명, Authorize 버튼(JWT 입력 후 보호된 엔드포인트 호출) 확인

## 향후 고려사항

- `application-prod.yml` 실값 채우기 — 스토어 출시 시점
- 배포 자동화가 생기면 `SPRING_PROFILES_ACTIVE` 서버 주입 방식 정리 필요 (현재는 수동 규약)
- `@ApiResponses` 에러코드 매트릭스 — 필요해지면 추가
