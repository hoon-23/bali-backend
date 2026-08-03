# Workout Insight Backend

개인 운동 기록을 관리하고, 매주 Claude API 기반 배치 분석으로 운동 패턴 인사이트를
제공하는 백엔드 서비스입니다.

## 개요

- 사용자는 Push/Pull/Legs/Strength 같은 카테고리로 운동 템플릿을 만들고, 이를 기반으로
  날짜별 세션을 기록합니다.
- 매주 월요일, Spring Batch가 각 사용자의 최근 7일 운동 기록을 집계해 Claude API로
  분석을 요청하고, 결과를 주간 인사이트로 저장합니다.
- 사용자는 API를 통해 자신의 주간 인사이트를 조회할 수 있습니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot |
| Architecture | 라이트 헥사고날 (모듈별 분리) |
| DB | PostgreSQL + JPA |
| Batch | Spring Batch |
| AI | Claude API (Anthropic) |
| Auth | Google OAuth2 + JWT |
| Test | Kotest, Testcontainers |
| CI/CD | GitHub Actions |
| Infra | Docker |

## 모듈 구조

```
bali_backend/
├── bali-core/     # 도메인 모델, 유스케이스, 포트 인터페이스
├── bali-infra/    # JPA 구현체, Claude API 어댑터
├── bali-api/      # REST API 서버 (인증, 컨트롤러)
└── bali-batch/    # Spring Batch 주간 분석 잡 (독립 실행)
```

`bali-api`와 `bali-batch`는 `bali-core`/`bali-infra`를 공유하지만 서로 독립적으로
배포/스케일할 수 있습니다.

## 핵심 기능

- **운동 템플릿 & 세션 기록**: 카테고리별 템플릿을 만들고, 날짜에 배정해 실제 수행
  결과(무게·횟수·세트 또는 페이스·시간)를 기록합니다.
- **종목 카탈로그 & 유사 종목 제안**: 새 종목 입력 시 기존 카탈로그에서 유사한 이름을
  제안해 데이터 정합성을 사용자가 직접 관리할 수 있습니다.
- **주간 배치 분석**: Spring Batch가 사용자 단위로 지난 7일 기록을 집계하고 Claude API로
  분석을 요청합니다. 사용자 단위 실패 격리와 재시작(restart)을 지원합니다.
- **주간 인사이트 조회**: 저장된 분석 결과를 API로 조회합니다.

자세한 설계는 [`docs/specs/`](./docs/specs) 를 참고하세요.

## 실행 방법

```bash
./gradlew build
docker compose up -d   # PostgreSQL 등 인프라 구동
./gradlew :bali-api:bootRun
```

## 테스트

```bash
./gradlew test
```
