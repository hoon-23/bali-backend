# 운동 템플릿 / 세션 기록 — 설계 문서

## 배경

`docs/specs/2026-08-03-workout-insight-backend-design.md` (Phase 1 스펙)에서 정의한 범위 중,
운동 종목 카탈로그(`docs/specs/2026-08-05-exercise-catalog-design.md`, 완료) 다음 단계인
**운동 템플릿(WorkoutTemplate)과 세션 기록(WorkoutSession)**을 다룬다.

템플릿은 Push/Pull/Legs/Strength 같은 루틴을 미리 정의해두는 개념이고, 세션은 특정 날짜에
실제로 수행한 기록이다. 세션은 템플릿에서 복사되어 생성되지만, 생성 후에는 실제 헬스장
상황(기구 점유 등)에 맞춰 자유롭게 종목을 추가/변경/삭제할 수 있어야 한다.

## 범위

- `WorkoutTemplate`/`TemplateItem`, `WorkoutSession`/`SessionLog` 도메인 모델
- 템플릿 CRUD API 5개, 세션 API 5개 (Phase 1 스펙의 API 설계 절 기준)
- Flyway 마이그레이션 (templates/template_items/sessions/session_logs 테이블)

범위 밖:
- **템플릿 공유 기능** — 2026-08-06 브레인스토밍 중 논의됐으나 Phase 2로 이연. 다른 유저의
  `PUBLIC` 템플릿을 조회해서 클라이언트가 복사(POST)하는 "fork" 모델로 하기로 결정.
  `WorkoutSession.templateId`가 본인 소유가 아닌 템플릿을 참조하는 "진짜 공유" 모델은 채택하지 않음.
- 주간 배치 분석, 주간 리포트 조회 (별도 스펙)
- Swagger/OpenAPI 부착 — 도메인 로직과 무관한 인프라 작업이라 별도의 작은 스펙으로 분리
- Airflow를 통한 배치 오케스트레이션 — 배치 분석 모듈 설계 시 다룸

## 모듈 구조

기존 `exercise` 도메인과 동일한 라이트 헥사고날 패턴을 따른다. Application service(usecase) 레이어는
두지 않는다 — Phase 1 스펙(`docs/specs/2026-08-03-workout-insight-backend-design.md`)이 정의한
"라이트 헥사고날"은 모든 레이어(domain/application/infra/interface)를 기계적으로 두는 게 아니라,
**테스트 격리가 실제로 필요한 지점(DB, Claude API)에만** 포트/어댑터를 적용하는 것이다. 지금 컨트롤러가
하는 일(연관 엔티티 조회 → 도메인 팩토리 호출 → 저장)은 리포지토리 1~2개를 얇게 오케스트레이션하는
수준이라, usecase 클래스를 두면 리포지토리 호출을 그대로 감싸기만 하는 빈 껍데기가 된다. 실제 비즈니스
판단(STRENGTH/CARDIO 필드 검증 등)은 이미 도메인 팩토리(`TemplateItem.create()`, `SessionLog.create()`)에
있으므로 usecase가 없어도 컨트롤러로 비즈니스 로직이 새는 문제는 없다.

**트레이드오프**: usecase/서비스 레이어가 없다 보니, 컨트롤러가 순수 인터페이스 어댑터를 넘어서는
오케스트레이션(여러 리포지토리 호출을 묶는 트랜잭션 경계 등)을 떠안는 경우가 생긴다. 예를 들어
`PATCH /sessions/{id}`(addItems/updateItems/removeLogIds)는 여러 리포지토리 호출을 한 요청으로
묶어야 하는데, 서비스 레이어가 있었다면 거기 자연스럽게 `@Transactional`을 붙였을 것을, 대신
컨트롤러 메서드에 직접 붙이는 방식으로 처리한다 (2026-08-06 Task 10 리뷰에서 실제로 발견된 케이스).

**usecase가 정당화되는 시점**: 여러 애그리거트를 넘나드는 진짜 멀티스텝 오케스트레이션이 필요해지면
(예: Phase 1 스펙의 주간 배치 분석 — 세션 집계 → Claude API 호출 → WeeklyAnalysis/Insight 저장) 그때는
별도 클래스(혹은 Spring Batch의 `ItemProcessor` 자체)가 그 역할을 맡는 게 맞다. CRUD 수준에서
선제적으로 두기보다 필요해지는 시점에 추가한다.

```
bali-core/template/
├── WorkoutTemplate.kt
├── TemplateCategory.kt        # PUSH | PULL | LEGS | STRENGTH
├── TemplateItem.kt
└── WorkoutTemplateRepository.kt

bali-core/session/
├── WorkoutSession.kt
├── SessionLog.kt
└── WorkoutSessionRepository.kt

bali-infra/template/
├── WorkoutTemplateJpaEntity.kt / TemplateItemJpaEntity.kt
├── WorkoutTemplateJpaRepository.kt / TemplateItemJpaRepository.kt
└── WorkoutTemplateRepositoryAdapter.kt

bali-infra/session/
├── WorkoutSessionJpaEntity.kt / SessionLogJpaEntity.kt
├── WorkoutSessionJpaRepository.kt / SessionLogJpaRepository.kt
└── WorkoutSessionRepositoryAdapter.kt

bali-api/template/
├── TemplateController.kt
├── TemplateResponse.kt / TemplateItemResponse.kt
└── TemplateCreateRequest.kt / TemplateItemRequest.kt

bali-api/session/
├── SessionController.kt
├── SessionResponse.kt / SessionLogResponse.kt
└── SessionCreateRequest.kt / SessionPatchRequest.kt / SessionLogPatchRequest.kt
```

## 도메인 모델

```kotlin
enum class TemplateCategory { PUSH, PULL, LEGS, STRENGTH }

data class WorkoutTemplate(
    val id: UUID?,
    val userId: UUID,
    val category: TemplateCategory,
    val name: String,
    val deleted: Boolean = false,
    val items: List<TemplateItem>,
)

data class TemplateItem(
    val id: UUID?,
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeight: BigDecimal?,       // kg, 0.5 단위
    val targetDurationSeconds: Int?,
    val targetPace: String?,             // 자유 텍스트, 미입력 허용
) {
    companion object {
        // STRENGTH: sets/reps/weight 필수, duration/pace는 null이어야 함
        // CARDIO: duration 필수, sets/reps/weight는 null이어야 함 (pace는 선택)
        fun create(
            exerciseType: ExerciseType,
            exerciseId: UUID,
            sortOrder: Int,
            targetSets: Int?, targetReps: Int?, targetWeight: BigDecimal?,
            targetDurationSeconds: Int?, targetPace: String?,
        ): TemplateItem {
            when (exerciseType) {
                ExerciseType.STRENGTH -> {
                    require(targetSets != null && targetReps != null && targetWeight != null)
                    require(targetDurationSeconds == null && targetPace == null)
                }
                ExerciseType.CARDIO -> {
                    require(targetDurationSeconds != null)
                    require(targetSets == null && targetReps == null && targetWeight == null)
                }
            }
            return TemplateItem(null, exerciseId, sortOrder, targetSets, targetReps, targetWeight, targetDurationSeconds, targetPace)
        }
    }
}

data class WorkoutSession(
    val id: UUID?,
    val userId: UUID,
    val date: LocalDate,
    val templateId: UUID?,               // null이면 템플릿 없이 생성된 빈 세션
    val logs: List<SessionLog>,
)

data class SessionLog(
    val id: UUID?,
    val exerciseId: UUID,
    val sortOrder: Int,
    val completed: Boolean = false,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
) {
    companion object {
        // 템플릿에서 복사된 항목: target*는 TemplateItem 스냅샷, actual*는 null로 시작
        // 즉흥 추가 항목(세션 생성 후 PATCH로 추가): target*는 null 허용
        //   (웨이트 트레이닝 특성상 계획 없이 그 자리에서 하고 기록하는 흐름이라
        //    target을 강제하지 않음 — actual*만 채움)
        fun create(
            exerciseType: ExerciseType,
            exerciseId: UUID,
            sortOrder: Int,
            targetSets: Int?, targetReps: Int?, targetWeight: BigDecimal?,
            targetDurationSeconds: Int?, targetPace: String?,
        ): SessionLog {
            // target*는 전부 null이거나(즉흥 추가), exerciseType에 맞는 필드 그룹만 채워져 있어야 함
            val strengthFieldsSet = targetSets != null || targetReps != null || targetWeight != null
            val cardioFieldsSet = targetDurationSeconds != null || targetPace != null
            require(!(strengthFieldsSet && cardioFieldsSet))
            when (exerciseType) {
                ExerciseType.STRENGTH -> require(targetDurationSeconds == null && targetPace == null)
                ExerciseType.CARDIO -> require(targetSets == null && targetReps == null && targetWeight == null)
            }
            return SessionLog(
                id = null, exerciseId = exerciseId, sortOrder = sortOrder, completed = false,
                targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
                targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
                actualSets = null, actualReps = null, actualWeight = null,
                actualDurationSeconds = null, actualPace = null,
            )
        }
    }
}
```

- `TemplateItem`/`SessionLog`의 STRENGTH/CARDIO 필드 상호배타 검증은 `companion object`의
  `create` 팩토리에서만 수행한다. `Exercise.type` 조회가 필요해 순수 `init` 블록으로는 할 수
  없고, 서비스 계층(`TemplateService`/`SessionService`)이 `ExerciseRepository`로 타입을
  조회한 뒤 `create`를 호출한다. JPA 어댑터가 DB row를 도메인으로 복원할 때는 기본 생성자를
  직접 사용해 재검증하지 않는다 (이미 저장 시점에 검증된 데이터이므로, 매 조회마다
  `exercises`와 조인하는 비용을 피하기 위함).
- `WorkoutTemplate`/`WorkoutSession`은 각각 `items`/`logs`를 통째로 들고 있는 애그리거트다.
  `TemplateItem`/`SessionLog`는 부모 없이 독립적으로 조회/재사용되지 않는 종속 개념이라
  별도 리포지토리를 두지 않는다.
- 세션이 순서를 건너뛰고 수행되는 것(기구 점유 등으로 다다음 종목 먼저)은 `sortOrder`가
  "계획된 표시 순서"일 뿐 완료 처리에 순서 제약이 없어 자연스럽게 지원된다. 종목을
  변형해서 수행하는 것(벤치프레스 대신 덤벨프레스)은 아래 `PATCH /sessions/{id}`의
  `updateItems`로 기존 `SessionLog.exerciseId`를 바꾸는 방식으로 지원한다 (삭제 후 재추가가
  아니라 같은 log를 유지 — "원래 무엇을 계획했었는지" 흔적을 남기기 위함).

## API 설계

```
POST   /api/v1/templates                  # 템플릿 등록 (category, name, items[])
GET    /api/v1/templates                  # 내 템플릿 목록 (deleted=false만)
GET    /api/v1/templates/{id}
PUT    /api/v1/templates/{id}             # 전체 교체 (items 포함)
DELETE /api/v1/templates/{id}             # 소프트 삭제 (deleted=true)

POST   /api/v1/sessions                   # 세션 생성 (templateId 있으면 logs 스냅샷 복사, 없으면 빈 세션)
GET    /api/v1/sessions?from=&to=         # 기간별 세션 조회
GET    /api/v1/sessions/{id}
PATCH  /api/v1/sessions/{id}              # addItems / updateItems / removeLogIds
PATCH  /api/v1/sessions/{id}/logs/{logId} # actual값 기록 + completed 체크
```

```kotlin
data class TemplateCreateRequest(
    val category: TemplateCategory,
    val name: String,
    val items: List<TemplateItemRequest>,
)
data class TemplateItemRequest(
    val exerciseId: UUID,
    val sortOrder: Int,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
)

data class SessionCreateRequest(val date: LocalDate, val templateId: UUID?)

data class SessionPatchRequest(
    val addItems: List<TemplateItemRequest> = emptyList(),      // target*는 null 허용
    val updateItems: List<SessionLogUpdateItem> = emptyList(),  // logId 기준 부분 수정
    val removeLogIds: List<UUID> = emptyList(),
)
data class SessionLogUpdateItem(
    val logId: UUID,
    val exerciseId: UUID, val sortOrder: Int,          // logId 외엔 전체 교체이므로 필수
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,  // 도메인상 정상적으로 null 가능
    val targetDurationSeconds: Int?, val targetPace: String?,
)

data class SessionLogPatchRequest(
    val completed: Boolean?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
)
```

응답 DTO는 도메인 모델 필드를 그대로 노출한다 (`ExerciseResponse` 패턴과 동일, `id`는
non-null로 고정):

```kotlin
data class TemplateResponse(
    val id: UUID, val category: TemplateCategory, val name: String,
    val items: List<TemplateItemResponse>,
)
data class TemplateItemResponse(
    val id: UUID, val exerciseId: UUID, val sortOrder: Int,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
)

data class SessionResponse(
    val id: UUID, val date: LocalDate, val templateId: UUID?,
    val logs: List<SessionLogResponse>,
)
data class SessionLogResponse(
    val id: UUID, val exerciseId: UUID, val sortOrder: Int, val completed: Boolean,
    val targetSets: Int?, val targetReps: Int?, val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?, val targetPace: String?,
    val actualSets: Int?, val actualReps: Int?, val actualWeight: BigDecimal?,
    val actualDurationSeconds: Int?, val actualPace: String?,
)
```

**소유권 검증**: `GET/PUT/DELETE /templates/{id}`, `GET/PATCH /sessions/{id}`에서 리소스가
없거나 `userId`가 요청자와 다르면 404로 통일한다 (403 대신 — 다른 유저 소유 리소스의 존재
자체를 노출하지 않기 위함). `ExerciseController`와 동일하게 `SecurityContextHolder`에서
`currentUserId()`를 추출한다.

**PUT /templates/{id}는 항목 전체 교체다.** 클라이언트가 items 배열 전체를 다시 보내면
기존 `template_items`를 지우고 재삽입한다. 완전히 다른 루틴으로 갈아탈 때는 PUT으로 억지로
수정하지 않고 새 템플릿을 POST하는 것을 전제로 한다 (기존 템플릿은 과거 세션이 참조하므로
남겨둠).

**PATCH /sessions/{id}는 logId 기준 부분 수정이다.** 템플릿과 달리 이미 기록된
`actual*`/`completed` 값을 보존해야 하므로 전체 교체 대신 `addItems`(새 종목 추가,
target null 허용)/`updateItems`(기존 log의 exerciseId/target 변경)/`removeLogIds`(삭제)로
**언급되지 않은 log**는 그대로 유지한다. `updateItems`에 **포함된** log는 `logId`를 제외한
필드(`exerciseId`/`sortOrder`/`target*`) 전체를 항상 지정해야 한다 — 필드 단위 부분 patch가
아니라 "이 log의 계획 값 전체를 교체"하는 것으로 취급한다 (`target*`의 `null`이 도메인상
정상값이라 "값 없음"과 "변경 안 함"을 구분할 수 없기 때문). `actual*`/`completed`는 이
요청 바디에 아예 없으므로 항상 보존된다.

## 영속성 / 마이그레이션

```
bali-infra/src/main/resources/db/migration/
└── V6__create_templates_and_sessions_tables.sql
```

```sql
CREATE TABLE templates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE template_items (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL,
    sort_order INT NOT NULL,
    target_sets INT, target_reps INT, target_weight NUMERIC(6,2),
    target_duration_seconds INT, target_pace VARCHAR(50)
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    date DATE NOT NULL,
    template_id UUID REFERENCES templates(id)   -- nullable, 소프트 삭제라 ON DELETE 불필요
);

CREATE TABLE session_logs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL,
    sort_order INT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    target_sets INT, target_reps INT, target_weight NUMERIC(6,2),
    target_duration_seconds INT, target_pace VARCHAR(50),
    actual_sets INT, actual_reps INT, actual_weight NUMERIC(6,2),
    actual_duration_seconds INT, actual_pace VARCHAR(50)
);
```

- `templates.deleted`로 소프트 삭제한다. 템플릿이 삭제돼도 `sessions.template_id`가 가리키는
  과거 참조는 그대로 유효하다 (유저 탈퇴의 soft delete 패턴과 동일한 이유 — 배치 분석/리포트가
  과거 데이터를 참조).
- `template_items`/`session_logs`는 부모 삭제 시 `ON DELETE CASCADE` (템플릿/세션을 실제로
  하드 삭제할 경로는 없지만 방어적으로 설정).
- `template_items.exercise_id`/`session_logs.exercise_id`는 `exercises(id)`를 참조하지만
  FK 제약은 걸지 않는다 (기존 `exercises` 테이블도 자체 FK 없이 애플리케이션 레벨에서만
  검증하는 컨벤션을 따름 — `Exercise.scope`/`ownerId` 검증과 동일한 결의 트레이드오프).

## 에러 처리

- 도메인 검증 실패 (`TemplateItem.create`/`SessionLog.create`의 STRENGTH/CARDIO 필드
  불일치, `PATCH /sessions/{id}/logs/{logId}`로 기록하는 `actual*` 값이 해당 log의
  종목 타입과 맞지 않는 경우도 동일 규칙 적용) → 기존 `@RestControllerAdvice`가
  `IllegalArgumentException` → 400으로 처리 (추가 구현 불필요).
- 다른 유저 소유 템플릿/세션 접근 → 404.
- `PATCH /sessions/{id}`의 `updateItems`/`removeLogIds`에 존재하지 않는 `logId` → 400.
- `POST /sessions`에 존재하지 않거나 남의 소유인 `templateId` → 404.
- `muscleGroup`/`type`류와 마찬가지로 `TemplateCategory` 잘못된 enum 값은 Jackson
  역직렬화 단계에서 이미 400 처리됨.

## 테스트 전략

기존 컨벤션을 그대로 따른다 (`ExerciseTest`/`ExerciseRepositoryAdapterTest`/
`ExerciseControllerTest` 패턴).

- `bali-core`: Kotest `StringSpec` — `TemplateItem.create`/`SessionLog.create`의
  STRENGTH/CARDIO 검증, 템플릿→세션 스냅샷 복사 로직
- `bali-infra`: `@DataJpaTest` + 로컬 docker-compose Postgres — CRUD, 소프트 삭제
  필터링(`GET /templates`가 삭제된 항목 제외), `updateItems`/`removeLogIds` 부분 수정이
  언급 안 된 log를 보존하는지
- `bali-api`: `@SpringBootTest` + `MockMvc` — 전체 엔드포인트, 소유권 404 케이스, 즉흥
  추가 종목(target null) 케이스, 종목 변형(`updateItems`로 exerciseId 변경) 케이스

## 향후 고려사항 (이 문서 범위 밖)

- **템플릿 공유 (Phase 2)**: 다른 유저의 `PUBLIC` 템플릿을 조회 가능하게 하고, 클라이언트가
  그 내용으로 새 `POST /templates`를 호출해 자기 계정에 복사(fork)하는 모델. `templates`에
  `visibility` 필드 추가가 필요하며, `WorkoutSession.templateId`가 본인 소유가 아닌 템플릿을
  참조하는 "진짜 공유" 모델은 채택하지 않는다.
- Swagger/OpenAPI 부착은 별도 스펙에서 다룬다.
