# 홈트(맨몸 매트 운동) 지원 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 맨몸 홈트 종목 12종을 시딩하고 `equipment` 필터를 제공하며, 주간/월간 분석이 맨몸 기록을 0으로 흘리지 않게 한다.

**Architecture:** 종목 변경은 Flyway V26 한 개로 처리한다. 통계는 무게 볼륨 필드를 그대로 두고 `AnalysisSummary`에 맨몸 지표 3개(기본값 있음)를 추가하며, 주간/월간이 공유하는 계산은 신규 `BodyweightStats` 오브젝트로 모은다.

**Tech Stack:** Kotlin, Spring Boot, Flyway, JPA, Kotest(core), JUnit5 + MockMvc(api/infra), 로컬 Postgres(`docker compose`, localhost:5432).

**Spec:** `docs/specs/2026-10-01-home-training-bodyweight-design.md`

## Global Constraints

- 커밋 메시지는 한국어, 접두사(feat/fix/test 등)는 영어. `Co-Authored-By`/`Claude-Session` 트레일러는 붙이지 않는다.
- 작업 브랜치는 `develop`. 모든 주석은 메서드당 한 줄(한국어) + 복잡한 로직에만 추가, 기존 파일의 주석 밀도에 맞춘다.
- 맨몸 기록은 STRENGTH, `weight=0`, 반복수 기준. 측정 단위(시간) 추가 금지, 반복수 권장 범위 저장 금지.
- `volumeByExercise` / `volumeByMuscleGroup` / `volumeChangeFromLastWeekPercent`의 의미와 형태는 바꾸지 않는다(무게 볼륨 전용).
- 신규 `AnalysisSummary` 필드는 반드시 기본값을 가진다(기존 JSONB 행 역직렬화 호환).
- 프론트 파일은 직접 수정하지 않는다. 프론트 전달은 SendMessage로만 한다.
- 테스트는 로컬 Postgres가 떠 있어야 한다: `docker compose up -d postgres`(없으면 `docker compose ps`로 서비스명 확인).

## Review Focus

1. 맨몸 종목 로그가 완료됐지만 `actualReps`/`actualSets`가 null인 경우: 반복수 0으로 취급하고 맵에서 제외한다. (Task 3 테스트)
2. 지난 기간 맨몸 반복수가 없거나 0인 경우: 증감률은 null이고 인사이트를 만들지 않는다. (Task 3, 4 테스트)
3. 맨몸 근육군(FUNCTIONAL 등)이 무게 볼륨 0이라도 근육군 비중 인사이트에서 "낮은 편"으로 오판되지 않는다. (Task 3 테스트)
4. 신규 필드가 없는 기존 summary JSON이 역직렬화된다. (Task 5 테스트)
5. 삭제 대상 3종을 세션/템플릿이 참조 중이면 마이그레이션이 실패하지 않고 그 행만 남는다. (Task 1 SQL의 `NOT EXISTS`, 수동 검증 단계)

---

### Task 1: V26 마이그레이션 (버티기 3종 삭제 + 맨몸 12종 시딩)

**Files:**
- Create: `bali-infra/src/main/resources/db/migration/V26__home_training_bodyweight_catalog.sql`
- Test: `bali-infra/src/test/kotlin/com/bali/infra/exercise/ExerciseRepositoryAdapterTest.kt` (테스트 메서드 추가)

**Interfaces:**
- Consumes: 기존 `exercises` 테이블(`id, name, variant, muscle_group, type, scope, owner_id, equipment`), V23의 `session_logs.exercise_id`/`template_items.exercise_id` FK(`ON DELETE RESTRICT`), `ExerciseRepositoryAdapter.findVisibleTo(userId): List<Exercise>`.
- Produces: GLOBAL 종목 12종(아래 표). 이후 Task 2의 컨트롤러 테스트가 `BODYWEIGHT` 필터 결과에 `푸시업`/`크런치`가 포함되는지 검증한다.

| name | variant | muscle_group |
|---|---|---|
| 버피 | NULL | FUNCTIONAL |
| 숄더탭 | NULL | FUNCTIONAL |
| 푸시업 | 무릎 | CHEST |
| 크런치 | NULL | ABS |
| 바이시클크런치 | NULL | ABS |
| 리버스크런치 | NULL | ABS |
| 레그레이즈 | NULL | ABS |
| 파이크푸시업 | 무릎 | SHOULDER |
| 벤치딥 | NULL | TRICEPS |
| 푸시업 | 클로즈 | TRICEPS |
| 리버스스노우엔젤 | NULL | BACK |
| 프론Y레이즈 | NULL | BACK |

- [ ] **Step 1: 실패하는 테스트 작성**

`ExerciseRepositoryAdapterTest` 클래스 끝(마지막 `}` 직전)에 추가한다. 파일 상단 import에 `com.bali.core.exercise.Equipment`를 추가한다.

```kotlin
    // V26이 시간 버티기 운동 3종을 삭제했는지 확인
    @Test
    fun `V26 이후 버티기 운동 3종은 카탈로그에 없다`() {
        val names = adapter.findVisibleTo(UUID.randomUUID()).map { it.name }

        listOf("플랭크", "사이드플랭크", "할로우홀드").forEach {
            assertTrue(it !in names, "$it 가 아직 카탈로그에 남아 있다")
        }
    }

    // V26이 홈트용 맨몸 12종을 BODYWEIGHT GLOBAL로 시딩했는지 확인
    @Test
    fun `V26 이후 홈트 맨몸 12종이 BODYWEIGHT GLOBAL로 존재한다`() {
        val expected = listOf(
            Triple("버피", null, MuscleGroup.FUNCTIONAL),
            Triple("숄더탭", null, MuscleGroup.FUNCTIONAL),
            Triple("푸시업", "무릎", MuscleGroup.CHEST),
            Triple("크런치", null, MuscleGroup.ABS),
            Triple("바이시클크런치", null, MuscleGroup.ABS),
            Triple("리버스크런치", null, MuscleGroup.ABS),
            Triple("레그레이즈", null, MuscleGroup.ABS),
            Triple("파이크푸시업", "무릎", MuscleGroup.SHOULDER),
            Triple("벤치딥", null, MuscleGroup.TRICEPS),
            Triple("푸시업", "클로즈", MuscleGroup.TRICEPS),
            Triple("리버스스노우엔젤", null, MuscleGroup.BACK),
            Triple("프론Y레이즈", null, MuscleGroup.BACK),
        )
        val visible = adapter.findVisibleTo(UUID.randomUUID())

        expected.forEach { (name, variant, group) ->
            val found = visible.singleOrNull { it.name == name && it.variant == variant }
            assertTrue(found != null, "$name/$variant 가 없거나 중복이다")
            assertEquals(group, found!!.muscleGroup)
            assertEquals(Equipment.BODYWEIGHT, found.equipment)
            assertEquals(ExerciseScope.GLOBAL, found.scope)
            assertEquals(ExerciseType.STRENGTH, found.type)
        }
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.exercise.ExerciseRepositoryAdapterTest"`
Expected: 두 신규 테스트 FAIL (플랭크가 남아 있고 12종이 없음). 기존 테스트는 PASS.

- [ ] **Step 3: 마이그레이션 작성**

`V26__home_training_bodyweight_catalog.sql`:

```sql
-- 홈트(맨몸 매트 운동) 지원(2026-10-01). 시간으로 버티는 플랭크 계열은 reps 필수 구조와 맞지 않아
-- 카탈로그에서 제거하고, 스크린샷 기준 부위별 맨몸 종목 12종을 추가한다.
-- session_logs/template_items가 참조 중인 행은 FK(RESTRICT)로 CD가 깨지지 않게 남겨두고 수동 정리한다.
DELETE FROM exercises e
WHERE e.scope = 'GLOBAL'
  AND e.variant IS NULL
  AND e.name IN ('플랭크', '사이드플랭크', '할로우홀드')
  AND NOT EXISTS (SELECT 1 FROM session_logs s WHERE s.exercise_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM template_items t WHERE t.exercise_id = e.id);

INSERT INTO exercises (id, name, variant, muscle_group, type, scope, owner_id, equipment) VALUES
(gen_random_uuid(), '버피', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '숄더탭', NULL, 'FUNCTIONAL', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '무릎', 'CHEST', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '바이시클크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '리버스크런치', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '레그레이즈', NULL, 'ABS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '파이크푸시업', '무릎', 'SHOULDER', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '벤치딥', NULL, 'TRICEPS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '푸시업', '클로즈', 'TRICEPS', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '리버스스노우엔젤', NULL, 'BACK', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT'),
(gen_random_uuid(), '프론Y레이즈', NULL, 'BACK', 'STRENGTH', 'GLOBAL', NULL, 'BODYWEIGHT');
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.exercise.ExerciseRepositoryAdapterTest"`
Expected: PASS. 로컬 DB에서 플랭크 등을 세션/템플릿이 참조 중이면 삭제 테스트만 FAIL하는데, 이는 `NOT EXISTS` 동작이 정상이라는 뜻이다. 그 경우 `SELECT name FROM exercises WHERE name IN ('플랭크','사이드플랭크','할로우홀드')`로 확인하고 이 로컬 행을 수동으로 정리한 뒤 재실행한다.

- [ ] **Step 5: Commit**

```bash
git add bali-infra/src/main/resources/db/migration/V26__home_training_bodyweight_catalog.sql bali-infra/src/test/kotlin/com/bali/infra/exercise/ExerciseRepositoryAdapterTest.kt
git commit -m "feat(infra): 버티기 운동 3종 삭제 및 홈트 맨몸 종목 12종 시딩"
```

---

### Task 2: `GET /api/v1/exercises?equipment=` 필터

**Files:**
- Modify: `bali-api/src/main/kotlin/com/bali/api/exercise/ExerciseController.kt` (`list`, 약 27-34행; import 추가)
- Test: `bali-api/src/test/kotlin/com/bali/api/exercise/ExerciseControllerTest.kt`

**Interfaces:**
- Consumes: Task 1의 시드(`푸시업` BODYWEIGHT, `크런치` ABS/BODYWEIGHT), `com.bali.core.exercise.Equipment`.
- Produces: `GET /api/v1/exercises?muscleGroup=&equipment=` — 두 파라미터는 선택이며 함께 주면 AND. 잘못된 enum 값은 Spring 기본 동작대로 400.

- [ ] **Step 1: 실패하는 테스트 작성**

`ExerciseControllerTest`의 `muscleGroup 필터` 테스트 아래에 추가한다.

```kotlin
    // equipment 쿼리 파라미터로 필터링했을 때 맨몸 종목만 반환하는지 확인
    @Test
    fun `equipment 필터로 GET exercises 호출하면 해당 장비 종목만 반환`() {
        val token = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/exercises")
                .param("equipment", "BODYWEIGHT")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].equipment", everyItem(equalTo("BODYWEIGHT"))))
            .andExpect(jsonPath("$[*].name", hasItem("푸시업")))
    }

    // muscleGroup과 equipment를 함께 주면 둘 다 만족하는 종목만 반환하는지 확인
    @Test
    fun `muscleGroup과 equipment를 함께 주면 AND로 필터링`() {
        val token = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/exercises")
                .param("muscleGroup", "ABS")
                .param("equipment", "BODYWEIGHT")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].muscleGroup", everyItem(equalTo("ABS"))))
            .andExpect(jsonPath("$[*].equipment", everyItem(equalTo("BODYWEIGHT"))))
            .andExpect(jsonPath("$[*].name", hasItem("크런치")))
    }

    // 정의되지 않은 equipment 값은 400을 반환하는지 확인
    @Test
    fun `알 수 없는 equipment 값이면 400 반환`() {
        val token = issueTokenForNewUser()

        mockMvc.perform(
            get("/api/v1/exercises")
                .param("equipment", "NOPE")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isBadRequest)
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.exercise.ExerciseControllerTest"`
Expected: 신규 3개 모두 FAIL (필터가 무시되어 비-BODYWEIGHT 종목이 섞이고, 알 수 없는 값도 400이 아닌 200을 반환).

- [ ] **Step 3: 최소 구현**

import에 `com.bali.core.exercise.Equipment`를 추가하고 `list`를 교체한다.

```kotlin
    // 카탈로그 조회 (GLOBAL 전체 + 본인 PERSONAL), muscleGroup/equipment로 선택적 필터링(함께 주면 AND)
    @Operation(summary = "종목 카탈로그 조회", description = "GLOBAL 전체 + 본인 PERSONAL 종목을 조회한다. muscleGroup, equipment로 선택적 필터링 가능(함께 주면 AND)")
    @GetMapping
    fun list(
        @RequestParam(required = false) muscleGroup: MuscleGroup?,
        @RequestParam(required = false) equipment: Equipment?,
    ): List<ExerciseResponse> {
        val visible = exerciseRepository.findVisibleTo(currentUserId())
        val filtered = visible
            .filter { muscleGroup == null || it.muscleGroup == muscleGroup }
            .filter { equipment == null || it.equipment == equipment }
        return filtered.map { ExerciseResponse.from(it) }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :bali-api:test --tests "com.bali.api.exercise.ExerciseControllerTest"`
Expected: PASS (기존 `muscleGroup 필터` 테스트 포함).

- [ ] **Step 5: Commit**

```bash
git add bali-api/src/main/kotlin/com/bali/api/exercise/ExerciseController.kt bali-api/src/test/kotlin/com/bali/api/exercise/ExerciseControllerTest.kt
git commit -m "feat(api): 종목 카탈로그 조회에 equipment 필터 추가"
```

---

### Task 3: AnalysisSummary 맨몸 지표 + BodyweightStats + 주간 계산 반영

**Files:**
- Modify: `bali-core/src/main/kotlin/com/bali/core/analysis/AnalysisSummary.kt`
- Create: `bali-core/src/main/kotlin/com/bali/core/analysis/BodyweightStats.kt`
- Modify: `bali-core/src/main/kotlin/com/bali/core/analysis/WeeklyStatsCalculator.kt` (`calculate`의 return 부분, `generateInsights`의 근육군 비중 블록)
- Test: `bali-core/src/test/kotlin/com/bali/core/analysis/WeeklyStatsCalculatorTest.kt`

**Interfaces:**
- Consumes: `SessionLog.actualSets/actualReps/completed/exerciseId`, `Exercise.equipment/muscleGroup`, `Equipment.BODYWEIGHT`, `Insight(id = null, summaryText = ...)`.
- Produces:
  - `AnalysisSummary`에 필드 3개 추가(끝에, 기본값 있음): `bodyweightRepsByExercise: Map<UUID, Int> = emptyMap()`, `setsByMuscleGroup: Map<MuscleGroup, Int> = emptyMap()`, `bodyweightRepsChangeFromLastWeekPercent: BigDecimal? = null`.
  - `object BodyweightStats` (Task 4가 그대로 사용):
    - `fun repsByExercise(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<UUID, Int>`
    - `fun setsByMuscleGroup(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<MuscleGroup, Int>`
    - `fun repsChangePercent(current: Map<UUID, Int>, previous: Map<UUID, Int>): BigDecimal?`
    - `fun repsChangeInsight(change: BigDecimal?, period: String, previousPeriod: String): Insight?`
    - `fun muscleShareInsight(setsByMuscleGroup: Map<MuscleGroup, Int>): Insight?`

- [ ] **Step 1: 실패하는 테스트 작성**

`WeeklyStatsCalculatorTest`를 다음과 같이 수정한다.

(a) import에 `com.bali.core.exercise.Equipment` 추가.

(b) `exercise` 헬퍼에 `equipment` 파라미터를 추가하고 맨몸 종목을 등록한다.

```kotlin
    val burpeeId = UUID.randomUUID()

    fun exercise(id: UUID, type: ExerciseType, muscleGroup: MuscleGroup, equipment: Equipment? = null) = Exercise(
        id = id, name = "test", variant = null, muscleGroup = muscleGroup, type = type, scope = ExerciseScope.GLOBAL, ownerId = null,
        equipment = equipment,
    )

    val exercisesById = mapOf(
        benchPressId to exercise(benchPressId, ExerciseType.STRENGTH, MuscleGroup.CHEST),
        squatId to exercise(squatId, ExerciseType.STRENGTH, MuscleGroup.LEGS),
        runningId to exercise(runningId, ExerciseType.CARDIO, MuscleGroup.CARDIO),
        burpeeId to exercise(burpeeId, ExerciseType.STRENGTH, MuscleGroup.FUNCTIONAL, Equipment.BODYWEIGHT),
    )
```

(c) 근육군 비중 관련 기존 3개 테스트를 세트 기준으로 교체한다(`불균형`, `하나뿐`, `아무 규칙도`).

```kotlin
    "근육군 중 하나의 세트 비중이 15% 미만이면 불균형 인사이트가 생성된다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 9, MuscleGroup.LEGS to 1),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("하체 비중이 10.0%로 낮은 편이에요")
    }

    "근육군이 하나뿐이면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 9),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "아무 규칙도 해당하지 않으면 insights는 빈 리스트다" {
        val summary = AnalysisSummary(
            60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), BigDecimal("2.0"),
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.LEGS to 5),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }

    "무게 볼륨이 0인 맨몸 근육군도 세트 비중이 충분하면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(),
            mapOf(MuscleGroup.CHEST to BigDecimal("900.0"), MuscleGroup.FUNCTIONAL to BigDecimal.ZERO),
            0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.FUNCTIONAL to 5),
        )

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.isEmpty() shouldBe true
    }
```

(d) 맨몸 지표 테스트를 파일 끝(마지막 `})` 직전)에 추가한다.

```kotlin
    "맨몸 종목 로그는 bodyweightRepsByExercise에 sets*reps로 집계되고 무게 볼륨은 0이다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0"))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise shouldBe mapOf(burpeeId to 45)
        summary.volumeByExercise.getValue(burpeeId).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    "중량 종목은 bodyweightRepsByExercise에 포함되지 않는다" {
        val logs = listOf(strengthLog(benchPressId, completed = true))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "완료되지 않았거나 reps가 비어 있는 맨몸 로그는 bodyweightRepsByExercise에서 제외된다" {
        val incomplete = strengthLog(burpeeId, completed = false, weight = "0")
        val noReps = strengthLog(burpeeId, completed = true, weight = "0").copy(actualReps = null)

        val summary = WeeklyStatsCalculator.calculate(listOf(incomplete, noReps), exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise.isEmpty() shouldBe true
    }

    "setsByMuscleGroup은 중량/맨몸 구분 없이 완료 세트를 근육군별로 합산한다" {
        val logs = listOf(
            strengthLog(benchPressId, completed = true, sets = 3),
            strengthLog(burpeeId, completed = true, sets = 4, weight = "0"),
            strengthLog(squatId, completed = false, sets = 5),
        )

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3, MuscleGroup.FUNCTIONAL to 4)
    }

    "지난주 맨몸 반복수 대비 증감률이 올바르게 계산된다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, sets = 3, reps = 15, weight = "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null, bodyweightRepsByExercise = mapOf(burpeeId to 30))

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.bodyweightRepsChangeFromLastWeekPercent shouldBe BigDecimal("50.0")
    }

    "지난주 맨몸 반복수가 없거나 0이면 증감률은 null이다" {
        val logs = listOf(strengthLog(burpeeId, completed = true, weight = "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        val summary = WeeklyStatsCalculator.calculate(logs, exercisesById, previousSummary = previous)

        summary.bodyweightRepsChangeFromLastWeekPercent shouldBe null
    }

    "맨몸 반복수가 지난주보다 10% 이상 늘면 증가 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 맨몸 운동 반복수가 지난주보다 50.0% 증가했어요")
    }

    "맨몸 반복수가 지난주보다 10% 이상 줄면 감소 인사이트가 생성된다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"))

        val insights = WeeklyStatsCalculator.generateInsights(summary)

        insights.map { it.summaryText } shouldBe listOf("이번 주 맨몸 운동 반복수가 지난주보다 20.0% 감소했어요")
    }

    "맨몸 반복수 증감이 10% 미만이면 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("5.0"))

        WeeklyStatsCalculator.generateInsights(summary).isEmpty() shouldBe true
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-core:test --tests "com.bali.core.analysis.WeeklyStatsCalculatorTest"`
Expected: 컴파일 에러(`setsByMuscleGroup` 등 파라미터 없음) 또는 FAIL.

- [ ] **Step 3: 구현**

`AnalysisSummary.kt`의 필드 끝에 추가:

```kotlin
    val bodyweightRepsByExercise: Map<UUID, Int> = emptyMap(),
    val setsByMuscleGroup: Map<MuscleGroup, Int> = emptyMap(),
    val bodyweightRepsChangeFromLastWeekPercent: BigDecimal? = null,
```

`BodyweightStats.kt` 신규:

```kotlin
package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

// 맨몸 운동 반복수/근육군별 세트 수 집계와 관련 인사이트를 계산하는 주간/월간 공용 순수 함수 모음
object BodyweightStats {

    private val REPS_CHANGE_THRESHOLD = BigDecimal(10)
    private val LOW_MUSCLE_GROUP_SHARE_THRESHOLD = BigDecimal(15)

    // 완료된 STRENGTH 로그 중 BODYWEIGHT 종목의 종목별 총 반복수(sets*reps). 0인 종목은 제외
    fun repsByExercise(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<UUID, Int> =
        strengthLogs
            .filter { exercisesById.getValue(it.exerciseId).equipment == Equipment.BODYWEIGHT }
            .groupBy { it.exerciseId }
            .mapValues { (_, logs) -> logs.sumOf { (it.actualSets ?: 0) * (it.actualReps ?: 0) } }
            .filterValues { it > 0 }

    // 완료된 STRENGTH 로그의 근육군별 세트 수(중량/맨몸 구분 없음). 0인 근육군은 제외
    fun setsByMuscleGroup(strengthLogs: List<SessionLog>, exercisesById: Map<UUID, Exercise>): Map<MuscleGroup, Int> =
        strengthLogs
            .groupBy { exercisesById.getValue(it.exerciseId).muscleGroup }
            .mapValues { (_, logs) -> logs.sumOf { it.actualSets ?: 0 } }
            .filterValues { it > 0 }

    // 맨몸 총 반복수의 직전 기간 대비 증감률(%). 직전 총합이 0이면 null
    fun repsChangePercent(current: Map<UUID, Int>, previous: Map<UUID, Int>): BigDecimal? {
        val previousTotal = previous.values.sum()
        if (previousTotal == 0) return null
        return BigDecimal(current.values.sum() - previousTotal).multiply(BigDecimal(100))
            .divide(BigDecimal(previousTotal), 1, RoundingMode.HALF_UP)
    }

    // 증감률이 ±10% 이상일 때만 인사이트를 만든다 (period 예: "이번 주"/"지난주")
    fun repsChangeInsight(change: BigDecimal?, period: String, previousPeriod: String): Insight? = when {
        change == null -> null
        change >= REPS_CHANGE_THRESHOLD ->
            Insight(id = null, summaryText = "$period 맨몸 운동 반복수가 ${previousPeriod}보다 ${change}% 증가했어요")
        change <= REPS_CHANGE_THRESHOLD.negate() ->
            Insight(id = null, summaryText = "$period 맨몸 운동 반복수가 ${previousPeriod}보다 ${change.abs()}% 감소했어요")
        else -> null
    }

    // 근육군이 2개 이상일 때 세트 비중이 가장 낮은 근육군이 15% 미만이면 불균형 인사이트를 만든다
    fun muscleShareInsight(setsByMuscleGroup: Map<MuscleGroup, Int>): Insight? {
        val total = setsByMuscleGroup.values.sum()
        if (setsByMuscleGroup.size < 2 || total == 0) return null
        val least = setsByMuscleGroup.entries.minBy { it.value }
        val share = BigDecimal(least.value).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP)
        return if (share < LOW_MUSCLE_GROUP_SHARE_THRESHOLD) {
            Insight(id = null, summaryText = "${least.key.displayName} 비중이 ${share}%로 낮은 편이에요")
        } else {
            null
        }
    }
}
```

`WeeklyStatsCalculator.kt` 수정:

`calculate`에서 `volumeChangeFromLastWeekPercent` 계산 아래에 추가하고, return의 `AnalysisSummary(...)`에 세 인자를 추가한다.

```kotlin
        val bodyweightRepsByExercise = BodyweightStats.repsByExercise(strengthLogs, exercisesById)
        val bodyweightRepsChange = previousSummary?.let {
            BodyweightStats.repsChangePercent(bodyweightRepsByExercise, it.bodyweightRepsByExercise)
        }
```
```kotlin
            volumeChangeFromLastWeekPercent = volumeChangeFromLastWeekPercent,
            bodyweightRepsByExercise = bodyweightRepsByExercise,
            setsByMuscleGroup = BodyweightStats.setsByMuscleGroup(strengthLogs, exercisesById),
            bodyweightRepsChangeFromLastWeekPercent = bodyweightRepsChange,
```

`generateInsights`의 `val totalMuscleVolume ...` 블록 전체(이 `if` 블록 포함)를 다음으로 교체한다. 사용하지 않게 된 `LOW_MUSCLE_GROUP_SHARE_THRESHOLD` 상수도 삭제한다.

```kotlin
        BodyweightStats.muscleShareInsight(summary.setsByMuscleGroup)?.let { insights += it }
        BodyweightStats.repsChangeInsight(summary.bodyweightRepsChangeFromLastWeekPercent, "이번 주", "지난주")?.let { insights += it }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :bali-core:test --tests "com.bali.core.analysis.WeeklyStatsCalculatorTest"`
Expected: PASS (기존 볼륨/완료율 테스트 포함).

- [ ] **Step 5: Commit**

```bash
git add bali-core/src/main/kotlin/com/bali/core/analysis bali-core/src/test/kotlin/com/bali/core/analysis/WeeklyStatsCalculatorTest.kt
git commit -m "feat(core): 주간 분석에 맨몸 반복수/근육군별 세트 수 집계 및 인사이트 추가"
```

---

### Task 4: 월간 계산 반영

**Files:**
- Modify: `bali-core/src/main/kotlin/com/bali/core/analysis/MonthlyStatsCalculator.kt`
- Create: `bali-core/src/test/kotlin/com/bali/core/analysis/MonthlyStatsCalculatorTest.kt`

**Interfaces:**
- Consumes: Task 3의 `BodyweightStats`와 `AnalysisSummary` 신규 필드.
- Produces: `MonthlyStatsCalculator.calculate`/`generateInsights`가 같은 필드를 채운다. 문구는 "이번 달"/"지난달".

- [ ] **Step 1: 실패하는 테스트 작성**

`MonthlyStatsCalculatorTest.kt` 신규:

```kotlin
package com.bali.core.analysis

import com.bali.core.exercise.Equipment
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.ExerciseType
import com.bali.core.exercise.MuscleGroup
import com.bali.core.session.SessionLog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class MonthlyStatsCalculatorTest : StringSpec({

    val benchPressId = UUID.randomUUID()
    val burpeeId = UUID.randomUUID()

    fun exercise(id: UUID, muscleGroup: MuscleGroup, equipment: Equipment? = null) = Exercise(
        id = id, name = "test", variant = null, muscleGroup = muscleGroup, type = ExerciseType.STRENGTH,
        scope = ExerciseScope.GLOBAL, ownerId = null, equipment = equipment,
    )

    val exercisesById = mapOf(
        benchPressId to exercise(benchPressId, MuscleGroup.CHEST),
        burpeeId to exercise(burpeeId, MuscleGroup.FUNCTIONAL, Equipment.BODYWEIGHT),
    )

    fun strengthLog(exerciseId: UUID, sets: Int, reps: Int, weight: String) =
        SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = sets, targetReps = reps, targetWeight = BigDecimal(weight))
            .copy(completed = true, actualSets = sets, actualReps = reps, actualWeight = BigDecimal(weight))

    "맨몸 종목은 bodyweightRepsByExercise에 집계되고 setsByMuscleGroup은 중량/맨몸을 함께 센다" {
        val logs = listOf(strengthLog(benchPressId, 3, 10, "60.0"), strengthLog(burpeeId, 4, 10, "0"))

        val summary = MonthlyStatsCalculator.calculate(logs, exercisesById, previousSummary = null)

        summary.bodyweightRepsByExercise shouldBe mapOf(burpeeId to 40)
        summary.setsByMuscleGroup shouldBe mapOf(MuscleGroup.CHEST to 3, MuscleGroup.FUNCTIONAL to 4)
    }

    "지난달 맨몸 반복수 대비 증감률이 계산되고 없거나 0이면 null이다" {
        val logs = listOf(strengthLog(burpeeId, 3, 20, "0"))
        val previous = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null, bodyweightRepsByExercise = mapOf(burpeeId to 40))
        val noPrevious = AnalysisSummary(0, emptyMap(), emptyMap(), 0, BigDecimal.ZERO, null)

        MonthlyStatsCalculator.calculate(logs, exercisesById, previous).bodyweightRepsChangeFromLastWeekPercent shouldBe BigDecimal("50.0")
        MonthlyStatsCalculator.calculate(logs, exercisesById, noPrevious).bodyweightRepsChangeFromLastWeekPercent shouldBe null
    }

    "맨몸 반복수 증감 인사이트는 이번 달/지난달 문구를 쓴다" {
        val up = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"))
        val down = AnalysisSummary(60, emptyMap(), emptyMap(), 0, BigDecimal("100.0"), null, bodyweightRepsChangeFromLastWeekPercent = BigDecimal("-20.0"))

        MonthlyStatsCalculator.generateInsights(up).map { it.summaryText } shouldBe listOf("이번 달 맨몸 운동 반복수가 지난달보다 50.0% 증가했어요")
        MonthlyStatsCalculator.generateInsights(down).map { it.summaryText } shouldBe listOf("이번 달 맨몸 운동 반복수가 지난달보다 20.0% 감소했어요")
    }

    "무게 볼륨이 0인 맨몸 근육군도 세트 비중이 충분하면 불균형 인사이트가 생성되지 않는다" {
        val summary = AnalysisSummary(
            60, emptyMap(), mapOf(MuscleGroup.CHEST to BigDecimal("900.0"), MuscleGroup.FUNCTIONAL to BigDecimal.ZERO),
            0, BigDecimal("100.0"), null,
            setsByMuscleGroup = mapOf(MuscleGroup.CHEST to 5, MuscleGroup.FUNCTIONAL to 5),
        )

        MonthlyStatsCalculator.generateInsights(summary).isEmpty() shouldBe true
    }
})
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-core:test --tests "com.bali.core.analysis.MonthlyStatsCalculatorTest"`
Expected: FAIL (신규 필드가 채워지지 않음 / 비중 인사이트가 볼륨 기준).

- [ ] **Step 3: 구현**

`MonthlyStatsCalculator.kt`에 Task 3의 Weekly와 같은 변경을 적용한다. `calculate`의 `volumeChangeFromLastMonthPercent` 아래:

```kotlin
        val bodyweightRepsByExercise = BodyweightStats.repsByExercise(strengthLogs, exercisesById)
        val bodyweightRepsChange = previousSummary?.let {
            BodyweightStats.repsChangePercent(bodyweightRepsByExercise, it.bodyweightRepsByExercise)
        }
```
return의 `AnalysisSummary(...)`에 추가:
```kotlin
            bodyweightRepsByExercise = bodyweightRepsByExercise,
            setsByMuscleGroup = BodyweightStats.setsByMuscleGroup(strengthLogs, exercisesById),
            bodyweightRepsChangeFromLastWeekPercent = bodyweightRepsChange,
```
`generateInsights`의 근육군 비중 블록과 `LOW_MUSCLE_GROUP_SHARE_THRESHOLD` 상수를 삭제하고 다음을 넣는다:
```kotlin
        BodyweightStats.muscleShareInsight(summary.setsByMuscleGroup)?.let { insights += it }
        BodyweightStats.repsChangeInsight(summary.bodyweightRepsChangeFromLastWeekPercent, "이번 달", "지난달")?.let { insights += it }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :bali-core:test`
Expected: core 전체 PASS (Weekly, Monthly, Daily 포함).

- [ ] **Step 5: Commit**

```bash
git add bali-core/src/main/kotlin/com/bali/core/analysis/MonthlyStatsCalculator.kt bali-core/src/test/kotlin/com/bali/core/analysis/MonthlyStatsCalculatorTest.kt
git commit -m "feat(core): 월간 분석에 맨몸 반복수/근육군별 세트 수 집계 및 인사이트 추가"
```

---

### Task 5: 응답 DTO 노출 + JSONB 하위 호환

**Files:**
- Modify: `bali-api/src/main/kotlin/com/bali/api/analysis/WeeklyAnalysisResponse.kt` (`AnalysisSummaryResponse`; 월간/현재주/현재월 응답이 이 DTO를 공유한다)
- Test: `bali-api/src/test/kotlin/com/bali/api/analysis/WeeklyAnalysisControllerTest.kt`
- Create: `bali-infra/src/test/kotlin/com/bali/infra/analysis/AnalysisSummaryJsonCompatTest.kt`

**Interfaces:**
- Consumes: Task 3의 `AnalysisSummary` 신규 필드.
- Produces: 주간/월간/현재주/현재월 응답의 `summary`에 `bodyweightRepsByExercise`, `setsByMuscleGroup`, `bodyweightRepsChangeFromLastWeekPercent`가 추가된다(프론트 전달 대상).

- [ ] **Step 1: 실패하는 테스트 작성**

`AnalysisSummaryJsonCompatTest.kt` 신규 (하위 호환 — 신규 필드가 없는 과거 JSON):

```kotlin
package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisSummary
import com.bali.core.exercise.MuscleGroup
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AnalysisSummaryJsonCompatTest {

    // 맨몸 지표 도입 전에 저장된 summary JSON이 기본값으로 역직렬화되는지 확인
    @Test
    fun `신규 필드가 없는 과거 summary JSON도 기본값으로 읽힌다`() {
        val oldJson = """
            {"totalWorkoutMinutes":60,"volumeByExercise":{},"volumeByMuscleGroup":{"CHEST":1000.0},
             "cardioTotalMinutes":0,"completionRate":100.0,"volumeChangeFromLastWeekPercent":null}
        """.trimIndent()

        val summary = jacksonObjectMapper().readValue(oldJson, AnalysisSummary::class.java)

        assertEquals(60, summary.totalWorkoutMinutes)
        assertEquals(BigDecimal("1000.0"), summary.volumeByMuscleGroup[MuscleGroup.CHEST])
        assertEquals(emptyMap<Any, Any>(), summary.bodyweightRepsByExercise)
        assertEquals(emptyMap<Any, Any>(), summary.setsByMuscleGroup)
        assertEquals(null, summary.bodyweightRepsChangeFromLastWeekPercent)
    }
}
```

`WeeklyAnalysisControllerTest`에 응답 노출 테스트를 추가한다(파일 상단 import에 필요한 `jsonPath`, `UUID`가 이미 있는지 확인하고 없으면 추가). 기존 `successSummary()`는 건드리지 말고 아래처럼 신규 필드를 채운 요약을 따로 만든다.

```kotlin
    @Test
    fun `GET analysis weekly 응답 summary에 맨몸 지표가 포함된다`() {
        val (token, userId) = issueTokenForNewUser()
        val exerciseId = UUID.randomUUID()
        analysisRepository.save(
            WeeklyAnalysis(
                id = null, userId = userId, weekOf = LocalDate.of(2026, 8, 3), status = AnalysisStatus.SUCCESS,
                summary = successSummary().copy(
                    bodyweightRepsByExercise = mapOf(exerciseId to 45),
                    setsByMuscleGroup = mapOf(MuscleGroup.FUNCTIONAL to 3),
                    bodyweightRepsChangeFromLastWeekPercent = BigDecimal("50.0"),
                ),
                insights = emptyList(),
            )
        )

        mockMvc.perform(get("/api/v1/analysis/weekly").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].summary.bodyweightRepsByExercise['$exerciseId']").value(45))
            .andExpect(jsonPath("$[0].summary.setsByMuscleGroup.FUNCTIONAL").value(3))
            .andExpect(jsonPath("$[0].summary.bodyweightRepsChangeFromLastWeekPercent").value(50.0))
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :bali-infra:test --tests "com.bali.infra.analysis.AnalysisSummaryJsonCompatTest" :bali-api:test --tests "com.bali.api.analysis.WeeklyAnalysisControllerTest"`
Expected: 호환 테스트는 Task 3 이후라 PASS(회귀 방지용), 컨트롤러 테스트는 FAIL(응답에 필드 없음).

- [ ] **Step 3: 구현**

`AnalysisSummaryResponse`에 필드를 추가하고 `from`에 매핑한다.

```kotlin
    val volumeChangeFromLastWeekPercent: BigDecimal?,
    val bodyweightRepsByExercise: Map<UUID, Int>,
    val setsByMuscleGroup: Map<MuscleGroup, Int>,
    val bodyweightRepsChangeFromLastWeekPercent: BigDecimal?,
```
```kotlin
            volumeChangeFromLastWeekPercent = summary.volumeChangeFromLastWeekPercent,
            bodyweightRepsByExercise = summary.bodyweightRepsByExercise,
            setsByMuscleGroup = summary.setsByMuscleGroup,
            bodyweightRepsChangeFromLastWeekPercent = summary.bodyweightRepsChangeFromLastWeekPercent,
```
컴파일 에러가 나는 다른 `AnalysisSummaryResponse(...)` 직접 생성자 호출이 있으면(grep) 같은 인자를 채운다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :bali-infra:test :bali-api:test`
Expected: PASS. 월간 컨트롤러 테스트(`MonthlyAnalysisControllerTest`)도 포함해 전부 통과해야 한다.

- [ ] **Step 5: Commit**

```bash
git add bali-api/src bali-infra/src/test/kotlin/com/bali/infra/analysis/AnalysisSummaryJsonCompatTest.kt
git commit -m "feat(api): 분석 응답에 맨몸 반복수/근육군별 세트 수 노출"
```

---

### Task 6: 전체 검증 및 프론트 전달

**Files:** 코드 변경 없음.

- [ ] **Step 1: 전체 빌드와 테스트**

Run: `./gradlew build`
Expected: 전 모듈 BUILD SUCCESSFUL. 실패하면 원인을 고쳐 해당 Task 커밋에 이어서 수정한다.

- [ ] **Step 2: 삭제 대상 참조 수동 확인(로컬 DB)**

Run: `docker compose exec postgres psql -U bali -d bali -c "SELECT name FROM exercises WHERE scope='GLOBAL' AND name IN ('플랭크','사이드플랭크','할로우홀드');"`
Expected: 0 rows. 남아 있으면 참조 중이라는 뜻이므로 사용자에게 보고한다(마이그레이션 의도된 동작).

- [ ] **Step 3: 프론트 세션에 전달**

`ListAgents`로 bali-frontend 세션 이름을 확인하고 `SendMessage`로 아래를 한 번에 보낸다(내용은 스펙의 "프론트" 섹션 + 실제 응답 필드):

1. `GET /api/v1/exercises?equipment=BODYWEIGHT` 로 홈트 필터 칩 구현 (`muscleGroup`과 AND 가능).
2. `equipment=BODYWEIGHT` 종목은 무게 입력 숨기고 `weight=0` 전송.
3. FUNCTIONAL("기능성 근력운동") 라벨/색/아이콘.
4. 분석 응답 `summary`에 신규 필드: `bodyweightRepsByExercise: {exerciseId: 총반복수}`, `setsByMuscleGroup: {MuscleGroup: 세트수}`, `bodyweightRepsChangeFromLastWeekPercent: number|null` (주간/월간/현재주/현재월 공통). 맨몸 종목은 `volumeByExercise`가 0이므로 볼륨 대신 반복수를 표시.
5. 삭제된 3종(플랭크, 사이드플랭크, 할로우홀드) 하드코딩/캐시 제거.

- [ ] **Step 4: 세션 정리**

앱 서버, Gradle 데몬(`./gradlew --stop`), docker compose(postgres/Airflow, `docker compose stop`)를 모두 종료한다.
