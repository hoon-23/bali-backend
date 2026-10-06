# Workout Insight Backend (Swayt)

개인 운동 기록을 관리하고, 주간/월간 운동 기록을 집계해 통계 기반 인사이트를
제공하는 백엔드 서비스입니다.

## 개요

- 사용자는 Push/Pull/Legs/Strength 같은 카테고리로 운동 템플릿을 만들고, 이를 기반으로
  날짜별 세션을 기록합니다.
- 매주/매월, 독립 배치 모듈이 각 사용자의 지난 기간 운동 기록을 집계해(총 운동시간, 종목별/
  근육군별 볼륨, 완료율, 운동 빈도(횟수)와 강도(세션당 평균)의 전 기간 대비 증감률) 규칙 기반
  인사이트 문장과 함께 저장합니다.
- 사용자는 API를 통해 자신의 주간/월간 인사이트와 일간/전체 기간 분석을 조회할 수 있습니다.

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
| Infra | Docker, AWS(ECS Fargate, RDS, ALB, CloudFront, EventBridge Scheduler; Terraform) |
| CI/CD | GitHub Actions |

## 모듈 구조

```
bali-backend/
├── bali-core/     # 도메인 모델, 포트 인터페이스
├── bali-infra/    # JPA 구현체, Flyway 마이그레이션
├── bali-api/      # REST API 서버 (인증, 컨트롤러)
├── bali-batch/    # 분석/알림/세션 정리 배치 (독립 실행, 웹 서버 없음)
├── airflow/       # 로컬용 Airflow DAG
└── infra/terraform/ # AWS 인프라 (dev)
```

`bali-api`와 `bali-batch`는 `bali-core`/`bali-infra`를 공유하지만 서로 독립적으로
배포/스케일할 수 있습니다.

## 핵심 기능

- **소셜 로그인**: Google/Kakao/Naver/Apple OIDC 검증(JWKS 캐싱, 제공자 장애 구분) 및 자체 JWT 발급, 자동 닉네임 생성.
- **운동 템플릿 & 세션**: 템플릿 기반 날짜별 세션 기록(무게/횟수/페이스/시간), 세트별 타이머, 상태 관리(SCHEDULED/IN_PROGRESS/COMPLETED/ABANDONED).
- **종목 카탈로그**: 새 종목 입력 시 유사 제안을 통한 데이터 정합성 관리.
- **유저 프로필**: 닉네임/목표 운동 횟수 관리, 연속 운동일·레벨/경험치 자동 계산.
- **주간/월간 분석**: 통계·규칙 기반 인사이트, 사용자 단위 실패 격리, 멱등적 재실행.
- **분석 조회**: 주간/월간·일간·전체 기간 분석 제공.
- **푸시 알림**: 주간/월간 요약, 루틴 리마인더, 미활동 알림, 앱 뱃지(미읽음 수).
- **무료 플랜 한도**: 루틴·개인 운동·월간 인사이트 한도 제어(기본 꺼짐).
- **개인정보 보호**: 탈퇴 시 PII 파기, [개인정보처리방침](./bali-api/src/main/resources/legal/privacy-policy.md) 공개 서빙.

배치 작업(`bali-batch`)은 로컬 Airflow DAG, dev 환경의 EventBridge Scheduler로 트리거되며 주간/월간 분석, 푸시, 알림, 세션 정리를 담당합니다.

API 명세는 서버 실행 후 `/swagger-ui.html`에서 확인할 수 있고, 자세한 설계 문서는
[`docs/specs/`](./docs/specs) 를 참고하세요.

## 실행 방법

```bash
docker compose up -d          # PostgreSQL, 로컬 Airflow(UI: localhost:8181) 구동
./gradlew build
./gradlew :bali-api:bootRun   # REST API 서버
./gradlew :bali-batch:bootRun # 주간 분석 배치 1회 실행 (평소엔 외부 스케줄러가 트리거)
```

## 테스트

로컬 PostgreSQL이 떠 있어야 합니다 (`docker compose up -d`).

```bash
./gradlew test
```

## 배포

GitHub Actions CI(`./gradlew test` + PostgreSQL)와 CD(develop push → ECR + ECS 재배포)를 활용합니다. 인프라는 Terraform(`infra/terraform/`)으로 관리하며, 운영 절차는 [`docs/runbooks/`](./docs/runbooks)를 참고하세요.
