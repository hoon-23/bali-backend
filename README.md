# Workout Insight Backend

개인 운동 기록을 관리하고, 매주 지난 7일간의 운동 기록을 집계해 통계 기반 주간
인사이트를 제공하는 백엔드 서비스입니다.

## 개요

- 사용자는 Push/Pull/Legs/Strength 같은 카테고리로 운동 템플릿을 만들고, 이를 기반으로
  날짜별 세션을 기록합니다.
- 매주, 독립 배치 모듈이 각 사용자의 최근 7일 운동 기록을 집계해(총 운동시간, 종목별/
  근육군별 볼륨, 완료율, 전주 대비 증감률) 규칙 기반 인사이트 문장과 함께 저장합니다.
- 사용자는 API를 통해 자신의 주간 인사이트를 조회할 수 있습니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot |
| Architecture | 라이트 헥사고날 (모듈별 분리) |
| DB | PostgreSQL + JPA (JSONB 컬럼 활용, Flyway 마이그레이션) |
| Batch | 순수 Kotlin 러너(`bali-batch`), 외부 스케줄러(Airflow)가 트리거 |
| Auth | 소셜 로그인(Google/Kakao/Naver/Apple) OIDC ID Token 검증 + 자체 JWT 발급 |
| API Docs | springdoc-openapi (Swagger UI) |
| Test | Kotest (도메인 단위), JUnit5 + 로컬 PostgreSQL (리포지토리/API 통합) |
| Infra | Docker |

## 모듈 구조

```
bali-backend/
├── bali-core/     # 도메인 모델, 포트 인터페이스
├── bali-infra/    # JPA 구현체, Flyway 마이그레이션
├── bali-api/      # REST API 서버 (인증, 컨트롤러)
└── bali-batch/    # 주간 분석 배치 (독립 실행, 웹 서버 없음)
```

`bali-api`와 `bali-batch`는 `bali-core`/`bali-infra`를 공유하지만 서로 독립적으로
배포/스케일할 수 있습니다.

## 핵심 기능

- **소셜 로그인**: Google/Kakao/Naver/Apple OIDC ID Token을 서버에서 검증(JWKS 캐시,
  iss/aud 검증, provider 장애 시 502 구분 응답)하고 자체 JWT를 발급합니다. 신규 가입 시
  닉네임을 자동 생성합니다.
- **운동 템플릿 & 세션 기록**: 카테고리별 템플릿을 만들고, 날짜에 배정해 실제 수행
  결과(무게·횟수·세트 또는 페이스·시간)를 기록합니다. 세트별 소요시간(세트 타이머)도
  함께 기록/조회할 수 있고, 세션은 SCHEDULED/IN_PROGRESS/COMPLETED 상태를 가집니다.
- **종목 카탈로그 & 유사 종목 제안**: 새 종목 입력 시 기존 카탈로그에서 유사한 이름을
  제안해 데이터 정합성을 사용자가 직접 관리할 수 있습니다.
- **유저 프로필 & 스트릭**: 닉네임/주간 목표 운동 횟수를 관리하고, 최근 운동 기록을
  기반으로 연속 운동일(스트릭)을 계산해 제공합니다.
- **주간 배치 분석**: `bali-batch`가 사용자 단위로 지난 7일 기록을 집계해 통계와 규칙
  기반 인사이트를 생성합니다. 사용자 단위 실패 격리를 지원하며, 재실행해도 멱등적으로
  동작합니다(같은 주는 항상 전체 재계산).
- **주간 인사이트 조회**: 저장된 분석 결과를 API로 조회합니다.
- **개인정보 보호(PIPA)**: 회원 탈퇴 시 email/providerId/nickname을 파기하며,
  [개인정보처리방침](./docs/legal/privacy-policy.md)을 문서화했습니다.

API 명세는 서버 실행 후 `/swagger-ui.html`에서 확인할 수 있고, 자세한 설계 문서는
[`docs/specs/`](./docs/specs) 를 참고하세요.

## 실행 방법

```bash
docker compose up -d          # PostgreSQL 등 인프라 구동
./gradlew build
./gradlew :bali-api:bootRun   # REST API 서버
./gradlew :bali-batch:bootRun # 주간 분석 배치 1회 실행 (평소엔 외부 스케줄러가 트리거)
```

## 테스트

로컬 PostgreSQL이 떠 있어야 합니다 (`docker compose up -d`).

```bash
./gradlew test
```
