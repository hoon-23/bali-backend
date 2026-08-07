# 주간 배치 분석 — 설계 문서

## 배경

`docs/specs/2026-08-03-workout-insight-backend-design.md` (Phase 1 스펙)에서 정의한 범위 중,
운동 종목 카탈로그 / 운동 템플릿 / 세션 기록에 이어 마지막 남은 도메인. 유저의 최근 7일 운동
기록을 매주 집계해서 `WeeklyAnalysis`/`Insight`로 저장하고, 조회 API로 제공한다.

원 스펙과 두 가지 지점에서 갈라진다:

1. **Claude API 미사용.** "일단 통계만으로 충분하다"는 판단에 따라 LLM 기반 자연어 인사이트
   생성을 빼고, 순수 집계 수치 + 규칙 기반(if/else) 문장으로 대체한다. `AnalysisPort`/Claude API
   어댑터, Spring Retry 백오프 등 원 스펙의 관련 설계는 전부 제외된다.
2. **Spring Batch 미사용, Airflow가 오케스트레이션.** 원 스펙은 Spring Batch(Job/Step/Chunk,
   skip 정책)로 "Claude API 실패 시 해당 유저만 건너뛰기"를 구현했다. Claude API가 빠지면서 그
   이유가 사라졌고, 스케줄링/재시도는 Airflow가 이미 맡는다. 순수 Kotlin 배치 러너(유저 순회 +
   try/catch)로 대체한다 — 이 규모(유저 수 수백~수천)에서는 Spring Batch의 chunk 커밋/재시작
   메커니즘이 주는 이득이 없고, 프레임워크 두 개가 재시도 책임을 나눠 가지면 오히려 복잡도만
   늘어난다. 유저 전체를 한 번에 메모리에 올리지 않도록 페이징 조회만 유지하면 처리량 문제도 없다.

## 범위

### Phase 1 (이 문서의 범위)
- 유저별 최근 7일 운동 기록 집계 (`bali-batch`, Airflow가 매주 트리거)
- 집계 지표: 총 운동시간, 종목별 볼륨, 근육군별 볼륨 분배, 유산소 총 시간, 완료율, 전주 대비 증감률
- 규칙 기반(비-LLM) 인사이트 문장 생성
- 주간 분석 결과 조회 API

### Phase 2 (범위 밖, 향후 별도 스펙)
- PR(개인 기록) 감지 — 종목별 전체 기간 최고 기록 갱신 여부. 최근 7일이 아닌 전체 히스토리를
  스캔해야 해서 이번 배치와 조회 비용 구조가 다름. ([[project_next_step_pr_detection_idea]])
- LLM 기반 자연어 인사이트 (Claude API 재도입) — 지금은 통계+규칙 문장으로 충분하다고 판단했지만,
  나중에 필요해지면 `AnalysisPort` 어댑터를 별도로 추가

## 모듈 구조

```
bali-batch/                              # 신규 Gradle 모듈, 독립 main() (bali-core/bali-infra만 의존)
└── src/main/kotlin/com/bali/batch/
    └── WeeklyAnalysisRunner.kt          # 엔트리포인트: 유저 순회 + try/catch
```

`bali-batch`는 `bali-api`와 무관하게 독립 실행된다. Airflow DAG가 `BashOperator`로
`java -jar bali-batch.jar` (또는 `./gradlew :bali-batch:run`)를 매주 월요일 실행하고, 러너가
직접 DB에 접근해 집계·저장까지 끝낸다 — 트리거용 HTTP 엔드포인트는 두지 않는다.

`bali-core`에 별도 usecase/서비스 레이어를 새로 만들지는 않는다. 대신 `WeeklyAnalysisRunner`
자체가 오케스트레이션(유저 순회, 실패 격리, 저장)을 맡는다. 지금까지 컨트롤러들은 리포지토리
1~2개를 얇게 호출하는 수준이라 서비스 레이어 없이도 충분했는데, 이 배치는 집계 계산 → 규칙 평가
→ 저장을 유저별 실패 격리까지 곁들여 실제로 조율해야 하는 다단계 작업이다. 이런 작업은 어차피
HTTP 요청/응답과 무관한 배치 전용 진입점이라 컨트롤러에 둘 수도 없으므로, `WeeklyAnalysisRunner`가
그 조율 역할을 맡는 게 자연스럽다 — 서비스 레이어가 필요해지면 언제든 추가할 수 있고, 지금 이
경우엔 오케스트레이터 클래스 자체가 그 역할을 겸한다.

## 도메인 모델

```kotlin
// bali-core/analysis
enum class AnalysisStatus { SUCCESS, FAILED, NO_ACTIVITY }

data class WeeklyAnalysis(
    val id: UUID? = null,
    val userId: UUID,
    val weekOf: LocalDate,           // 해당 주 월요일 날짜
    val status: AnalysisStatus,
    val summary: AnalysisSummary?,   // FAILED/NO_ACTIVITY면 null
    val insights: List<Insight>,
)

data class AnalysisSummary(
    val totalWorkoutMinutes: Int,      // STRENGTH 완료 로그 수 * 12분(추정) + cardioTotalMinutes, 아래 참고
    val volumeByExercise: Map<UUID, BigDecimal>,       // exerciseId -> 무게*횟수*세트 합
    val volumeByMuscleGroup: Map<MuscleGroup, BigDecimal>,
    val cardioTotalMinutes: Int,
    val completionRate: BigDecimal,                    // 완료 로그 수 / 전체 로그 수
    val volumeChangeFromLastWeekPercent: BigDecimal?,   // 지난주 분석 없으면 null
)

data class Insight(
    val id: UUID? = null,
    val summaryText: String,          // 규칙 하나당 문장 하나. WeeklyAnalysis에 종속되므로 자체 analysisId는 없음
                                       // (TemplateItem/SessionLog와 동일 패턴 — 부모 참조는 어댑터가 영속화 시점에 부여)
)
```

- `WeeklyAnalysis` 1—N `Insight` (같은 부모-자식 관계인 `WorkoutTemplate`—`TemplateItem`,
  `WorkoutSession`—`SessionLog`와 동일하게, `@OneToMany` 없이 어댑터에서 명시적으로 조합, `Insight`는
  `WeeklyAnalysis.insights` 필드로 임베드되고 자체 `analysisId`는 갖지 않음)
- **`totalWorkoutMinutes` 추정 근거**: STRENGTH 운동은 세트/반복/무게만 기록하고 소요 시간을 저장하지
  않는다 (실시간 타이머 기능은 별도 아이디어로 기록, [[project_next_step_set_timer_idea]]). 사용자의
  실제 3년치 기록 기준 "세트 사이 휴식 제외하고 종목당 평균 10~15분"이라는 데이터가 있어, 중간값인
  **완료된 STRENGTH 로그 1개당 12분**으로 추정하고 CARDIO는 `actualDurationSeconds` 합산을 그대로
  더한다. 타이머 기능이 생기면 이 추정치를 실측값으로 교체한다.
- `AnalysisSummary`는 JPA 엔티티에서 JSONB 컬럼 하나로 직렬화되어 저장된다 (조회 전용 요약값이라
  정규화하지 않음)
- `(userId, weekOf)` 유니크 — 재실행 시 기존 레코드를 지우고 다시 만드는 전체 재계산 방식으로
  멱등성을 보장한다 (부분 병합이 아니라 always-full-replace — `WorkoutTemplate`의 PUT 전체교체와
  같은 패턴)

## 배치 흐름

```
WeeklyAnalysisRunner.main()
├── ACTIVE 유저 전체 조회 (findAllByStatus(ACTIVE): List<User>, 페이징 없음 —
│     bali-core는 Spring 의존성이 전혀 없어 Pageable을 포트에 넣으면 프레임워크가
│     새고, 이 프로젝트 규모(유저 수백~수천)에서는 페이징 없이도 문제없음. 기존
│     UserRepository/ExerciseRepository 등 다른 포트에도 페이징 전례가 없어 일관성 유지)
└── 유저별로:
    ├── 최근 7일 WorkoutSession/SessionLog 조회
    ├── 세션이 하나도 없으면 → status=NO_ACTIVITY 저장, 다음 유저
    ├── 6개 지표 계산 (지난주 WeeklyAnalysis를 조회해 증감률 계산, 없으면 null)
    ├── 규칙 평가 → Insight 0~N개 생성 (예: WoW 증감, 완료율 낮음, 근육군 불균형)
    ├── (userId, weekOf) 기존 WeeklyAnalysis/Insight 있으면 삭제 후 재생성
    └── 예외 발생 시 status=FAILED 저장 + 로그 남기고 다음 유저로 계속 (해당 유저만 건너뜀)
```

Airflow가 실패로 판단해 DAG를 재시도하면 전체 재실행이지만, 매 실행이 멱등적(전체 재계산)이라
이미 성공한 유저를 다시 처리해도 문제없다.

## API 설계

```
GET /api/v1/analysis/weekly            # 본인 전체 주간 분석 목록, weekOf desc
GET /api/v1/analysis/weekly/{weekOf}   # 특정 주 분석 (weekOf=ISO 날짜, 해당 주 월요일)
```

응답:

```kotlin
data class WeeklyAnalysisResponse(
    val weekOf: LocalDate,
    val status: AnalysisStatus,
    val summary: AnalysisSummaryResponse?,  // FAILED/NO_ACTIVITY면 null
    val insights: List<String>,
)

data class AnalysisSummaryResponse(
    val totalWorkoutMinutes: Int,
    val volumeByExercise: Map<UUID, BigDecimal>,
    val volumeByMuscleGroup: Map<MuscleGroup, BigDecimal>,
    val cardioTotalMinutes: Int,
    val completionRate: BigDecimal,
    val volumeChangeFromLastWeekPercent: BigDecimal?,
)
```

- 항상 `userId`로 스코프되므로("본인 것만 조회") 다른 유저 데이터 접근 개념 자체가 없다 — 존재하지
  않는 주 조회는 그냥 404
- `currentUserId()`는 기존 컨트롤러들과 동일하게 컨트롤러 내부에 중복 구현 (공용 인증 유틸리티 없음)

## 영속성/마이그레이션

```sql
-- V7__create_weekly_analyses_and_insights_tables.sql
CREATE TABLE weekly_analyses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    week_of DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    summary JSONB
);

CREATE TABLE insights (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES weekly_analyses(id) ON DELETE CASCADE,
    summary_text VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX idx_weekly_analyses_user_id_week_of ON weekly_analyses(user_id, week_of);
CREATE INDEX idx_insights_analysis_id ON insights(analysis_id);
```

`template_id`/`session_id`와 동일하게 같은 애그리거트 내부 부모-자식 참조(`analysis_id`)는 FK
제약을 두고, `exercise_id`처럼 애그리거트 경계를 넘는 참조만 FK 없이 둔다 (기존 컨벤션 유지).

## 에러 처리

- `status=FAILED`인 주도 200으로 반환하되 `summary`는 `null`, `insights`는 빈 배열 —
  프론트에서 "이번 주 분석 실패"로 표시 가능
- 배치 자체의 유저별 실패 처리는 위 배치 흐름에 있음 (FAILED 저장 후 다음 유저 계속, 배치 전체는
  중단되지 않음)
- JWT 인증 실패/만료 → 401 (기존 패턴과 동일)

## 테스트 전략 (Kotest / JUnit5)

- `bali-core`: 집계 로직(볼륨/완료율/증감률 계산), 규칙 기반 문장 생성 로직 단위 테스트
- `bali-infra`: `WeeklyAnalysisRepositoryAdapter` — `@DataJpaTest`, JSONB 컬럼 read/write 검증
- `bali-batch`: 여러 유저 중 하나를 강제 실패시켜 나머지는 계속 처리되는지(격리) 검증 — 실제
  Postgres 대상 (`docker compose up -d`, Testcontainers 아님, 기존 프로젝트 컨벤션)
- `bali-api`: 목록/단건 조회 + 존재하지 않는 주 404 케이스 (MockMvc)

## 향후 고려사항

- PR(개인 기록) 감지 — Phase 2, [[project_next_step_pr_detection_idea]]
- LLM 기반 자연어 인사이트 재도입 — Phase 2, `AnalysisPort` 재추가
- Airflow 자체의 설치/DAG 등록은 이 스펙 범위 밖 (로컬에 아직 Airflow 환경이 없다면 별도로 준비
  필요)
