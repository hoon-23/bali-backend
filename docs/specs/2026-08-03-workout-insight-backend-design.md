# 운동 기록 LLM 분석 백엔드 — 설계 문서 (Phase 1)

## 배경

3년간 개인적으로 (애플 기본 메모 앱에) 기록해온 운동 기록을 정형화하고, Claude API를
활용한 주간 배치 분석으로 운동 패턴 인사이트를 제공하는 백엔드 서비스.

당초 1인용 개인 프로젝트로 러프 기획되었으나, 브레인스토밍 과정에서 멀티 유저 지원
구조로 범위를 확장하기로 결정했다. 인터뷰용 포트폴리오로 시작하되, 완성도에 따라
실제 앱 서비스로 전환할 가능성을 열어두고 설계한다.

## 범위 (Phase 1 / Phase 2)

이 문서는 **Phase 1 (MVP)** 범위를 다룬다. Phase 2는 향후 별도 스펙으로 브레인스토밍한다.

### Phase 1 (이 문서의 범위)
- Google OAuth 로그인 + 자체 JWT 발급, 회원 탈퇴(soft delete)
- 운동 종목 카탈로그 (글로벌 시드 + 개인 종목 추가 + 실시간 유사 종목 제안)
- 운동 템플릿(Push/Pull/Legs/Strength 카테고리) CRUD
- 템플릿 기반 세션 기록 (날짜 배정, 복사 후 자유 수정, 실제 수행값 입력)
- 주간 배치 분석 (Spring Batch, 매주 월요일, 유저별 처리, Claude API 연동)
- 주간 리포트 조회 API

### Phase 2 (범위 밖, 향후 별도 스펙)
- 자연어 질의 API ("이번 주 운동 패턴 어때?")
- 추가 소셜 로그인 (Kakao 등)
- 통계 대시보드

### 명시적으로 뺀 것 (트레이드오프 결정)
- **관리자에 의한 사후 종목 정규화 기능은 완전히 제외.** 사용자가 사후에 임의로
  종목이 변경됐다고 느낄 수 있어 신뢰도 문제가 될 수 있다는 판단. 대신 입력 시점의
  실시간 유사 종목 제안(trigram similarity)으로 사용자가 직접 정규화 여부를 결정하게 한다.
- **본인의 3년치 기존 기록 마이그레이션은 정식 API 기능이 아니다.** 자유 형식 메모
  데이터라 다른 유저의 과거 데이터 포맷을 가정할 수 없으므로, 본인 계정 시딩용
  일회성 스크립트로만 처리하고 API 스펙에 포함하지 않는다. 신규 유저는 가입 후
  템플릿/세션 기능으로 처음부터 기록을 시작한다.

## 모듈 구조

과도한 계층화 없이, 테스트 격리가 실제로 필요한 지점(DB, Claude API)에만 포트/어댑터를
적용하는 라이트 헥사고날 구조. Gradle 멀티모듈로 배치와 API 서버를 독립 배포 가능하게 분리한다.

```
bali_backend/
├── settings.gradle.kts
├── bali-core/     # 도메인 모델 + 유스케이스 + 필요한 포트 인터페이스만
│                  # (Repository 인터페이스, AnalysisPort 등)
├── bali-infra/    # JPA 구현체, Claude API 어댑터, PostgreSQL 설정 (core 의존)
├── bali-api/      # Spring Boot 웹 서버: REST 컨트롤러, 인증, main() (core+infra 의존)
└── bali-batch/    # Spring Batch 모듈: WeeklyAnalysisJob, 독립 main() (core+infra 의존)
```

`bali-api`와 `bali-batch`는 `bali-core`/`bali-infra`를 공유하되 서로 독립적으로
실행/배포 가능하다 (실무 배치 운영 패턴과 동일).

## 도메인 모델

```
domain/
├── user/       User(id, email, provider=GOOGLE, status=ACTIVE|WITHDRAWN)
├── exercise/   Exercise(id, name, type=STRENGTH|CARDIO, scope=GLOBAL|PERSONAL, ownerId?)
├── template/   WorkoutTemplate(id, userId, category=PUSH|PULL|LEGS|STRENGTH, name)
│               TemplateItem(id, templateId, exerciseId, targetSets/reps/weight | targetPace/duration)
├── session/    WorkoutSession(id, userId, date, templateId?)
│               SessionLog(id, sessionId, exerciseId, completed,
│                          actualSets/reps/weight | actualPace/duration)
├── analysis/   WeeklyAnalysis(id, userId, weekOf, status=SUCCESS|FAILED|NO_ACTIVITY, aggregatedStats)
└── insight/    Insight(id, analysisId, summaryText)
```

핵심 관계:
- `WorkoutTemplate` 1—N `TemplateItem` (각 아이템이 `Exercise` 참조)
- `WorkoutSession`은 템플릿에서 복사되어 생성되지만, 생성 후 아이템 추가/삭제/수정이 자유롭다
  (템플릿 참조는 유지 — 어떤 루틴 기반이었는지 분석에 활용)
- `Exercise.type`에 따라 `TemplateItem`/`SessionLog`에 허용되는 필드가 달라진다
  (STRENGTH: weight/reps/sets 필수, CARDIO: pace/duration 필수) — 도메인 검증 규칙
- `Exercise.scope=PERSONAL`인 개인 종목은 `ownerId`를 가지며 본인만 조회/사용 가능

## 인증 흐름

1. 클라이언트가 Google OAuth 로그인 → Google이 인가 코드 발급
2. 백엔드가 Spring Security OAuth2 Client로 Google 사용자 정보 획득
   → 최초 로그인 시 `User` 레코드 생성 (provider=GOOGLE)
3. 백엔드가 자체 JWT(Access Token) 발급 → 이후 모든 API는 `Authorization: Bearer <JWT>`로 인증
4. 회원 탈퇴는 soft delete (`status=WITHDRAWN`) — 배치 분석/리포트가 과거 데이터를
   참조하므로 완전 삭제 대신 비활성화 처리
5. 확장성: `AuthProvider` enum에 KAKAO 등 추가 시 Security 설정에 등록만 하면 되는 구조
   (Phase 1은 Google만 구현)

## API 설계

```
GET    /oauth2/authorization/google       # 로그인 시작 (Spring Security 기본 제공, 커스텀 컨트롤러 없음)
                                           # Google 콜백(/login/oauth2/code/google) 처리 후 JWT를 JSON으로 응답
DELETE /api/v1/users/me                   # 회원 탈퇴 (soft delete)

GET    /api/v1/exercises                  # 종목 카탈로그 조회 (글로벌+본인 개인 종목)
GET    /api/v1/exercises/suggest?q={text} # 유사 종목 제안 (trigram similarity)
POST   /api/v1/exercises                  # 개인 종목 등록

POST   /api/v1/templates                  # 운동 템플릿 등록 (category + items)
GET    /api/v1/templates                  # 내 템플릿 목록
GET    /api/v1/templates/{id}
PUT    /api/v1/templates/{id}
DELETE /api/v1/templates/{id}

POST   /api/v1/sessions                   # 세션 생성 (templateId로 복사 or 빈 세션)
GET    /api/v1/sessions?from=&to=         # 기간별 세션 조회
GET    /api/v1/sessions/{id}
PATCH  /api/v1/sessions/{id}              # 세션 아이템 추가/삭제/수정
PATCH  /api/v1/sessions/{id}/logs/{logId} # 실제 수행값 기록 + 완료 체크

GET    /api/v1/analysis/weekly            # 주간 분석 결과 목록
GET    /api/v1/analysis/weekly/{weekOf}   # 특정 주 분석 결과 조회
```

## 배치 흐름 & Claude API 연동

```
WeeklyAnalysisJob (매주 월요일 00:00, 스케줄러가 JobLauncher 트리거)
└── Step: analyzeUsersStep (chunk 기반)
    ├── ItemReader: ACTIVE 상태 유저를 페이징 조회
    ├── ItemProcessor: 유저별 최근 7일 세션/로그 집계
    │     (총 운동 시간, 종목별 볼륨=무게×횟수×세트, 유산소 총 시간, 완료율)
    │     → Claude API 호출 → 인사이트 텍스트 생성
    └── ItemWriter: WeeklyAnalysis + Insight 저장
```

- **멱등성**: `(userId, weekOf)` 유니크 제약으로 같은 주 중복 실행 방지
- **활동 없는 유저**: 세션 기록이 없으면 Claude 호출 없이 `status=NO_ACTIVITY`로 저장
  (불필요한 API 호출/비용 방지)
- **유저 단위 실패 격리**: Claude API 타임아웃/레이트리밋 등으로 특정 유저 처리 실패 시,
  Spring Batch의 skip 정책으로 해당 유저만 건너뛰고 나머지는 계속 진행
  → `WeeklyAnalysis.status = FAILED`로 남겨서 Job 재시작(restart) 시 실패한 유저만 재처리
- **포트 분리**: `AnalysisPort` 인터페이스 뒤에 실제 Claude API 어댑터를 두어,
  테스트 시 목 구현체로 대체 가능 (bali-core에 포트, bali-infra에 구현체)

## 에러 처리

- 도메인 검증 실패 (예: CARDIO 종목에 weight 입력) → 400 + 명확한 에러 코드
- JWT 인증 실패/만료 → 401
- Claude API 장애 → Spring Retry로 backoff 재시도 → 최종 실패 시 해당 유저 분석만
  FAILED 처리 (전체 배치는 중단되지 않음)

## 테스트 전략 (Kotest)

- 도메인 단위 테스트: 검증 규칙 (STRENGTH/CARDIO 필드 제약, 템플릿→세션 복사 로직 등)
- 리포지토리 테스트: Testcontainers로 실제 PostgreSQL 대상 (pg_trgm 유사도 검색 포함)
- 배치 테스트: Spring Batch Test (`JobLauncherTestUtils`)로 Step 단위 검증,
  유저별 실패 격리 시나리오 포함
- Claude API 연동 테스트: `AnalysisPort` 목 구현체로 대체 (실제 API 호출 없이 검증)
- CI: GitHub Actions에서 PR마다 빌드+테스트

## 공개 저장소 문서화 정책

이 프로젝트는 면접용 포트폴리오로 GitHub에 공개된다. 기존 README.md에는 이직 준비
상황, 면접 어필 포인트 등 비공개해야 할 개인 사정이 포함되어 있다.

- 기존 README.md 내용은 `.gitignore` 처리된 개인 메모 파일로 옮겨 로컬에서만 보관
- 공개용 README.md는 프로젝트 개요, 기술 스택, 아키텍처, API 요약, 실행 방법 등을
  담은 표준 오픈소스 프로젝트 README로 새로 작성
