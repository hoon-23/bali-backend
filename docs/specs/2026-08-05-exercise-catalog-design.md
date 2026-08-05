# 운동 종목 카탈로그 — 설계 문서

## 배경

`docs/specs/2026-08-03-workout-insight-backend-design.md` (Phase 1 스펙)에서 정의한 범위 중,
운동 템플릿/세션이 참조하는 선행 조건인 **운동 종목 카탈로그**를 다룬다.

글로벌 시드 데이터는 사용자가 3년간 개인적으로 기록해온 운동 메모(자유 형식 텍스트)에서
실제 사용한 종목명을 추출/정리해서 구성한다. 이 원본 기록에는 같은 종목을 가리키는
표기가 제각각인 경우(오타, 축약어)가 많았는데("바벨로우"/"바벨로"/"바벨로유", "벤치"/"밴치"),
이는 역설적으로 유사 종목 제안 기능이 왜 필요한지를 보여주는 실제 사례이기도 하다.

## 범위

- `Exercise` 도메인 모델
- API 3개: 카탈로그 조회, 유사 종목 제안(trigram), 개인 종목 등록
- Flyway 도입 (이 프로젝트 최초 — 기존엔 Hibernate `ddl-auto: update`만 사용)
- 글로벌 시드 데이터 (아래 "글로벌 시드 종목" 참고)

범위 밖: `WorkoutTemplate`/`WorkoutSession`/분석 도메인 (별도 스펙에서 다룸)

## 모듈 구조

기존 `user` 도메인과 동일한 라이트 헥사고날 패턴을 따른다.

```
bali-core/exercise/
├── Exercise.kt
├── ExerciseType.kt        # STRENGTH | CARDIO
├── ExerciseScope.kt       # GLOBAL | PERSONAL
├── MuscleGroup.kt         # BACK, CHEST, SHOULDER, BICEPS, TRICEPS, LEGS, ABS, CARDIO
└── ExerciseRepository.kt  # 포트 인터페이스

bali-infra/exercise/
├── ExerciseJpaEntity.kt
├── ExerciseJpaRepository.kt
└── ExerciseRepositoryAdapter.kt

bali-api/exercise/
├── ExerciseController.kt
├── ExerciseResponse.kt
└── ExerciseCreateRequest.kt
```

## 도메인 모델

```kotlin
enum class MuscleGroup { BACK, CHEST, SHOULDER, BICEPS, TRICEPS, LEGS, ABS, CARDIO }
enum class ExerciseType { STRENGTH, CARDIO }
enum class ExerciseScope { GLOBAL, PERSONAL }

data class Exercise(
    val id: UUID?,
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val scope: ExerciseScope,
    val ownerId: UUID?,
) {
    init {
        when (scope) {
            ExerciseScope.PERSONAL -> require(ownerId != null) { "PERSONAL exercise requires an ownerId" }
            ExerciseScope.GLOBAL -> require(ownerId == null) { "GLOBAL exercise must not have an ownerId" }
        }
    }
}
```

- `variant`는 그립/자세 등 세부 변형을 나타내는 선택 필드다 (예: `name="랫풀다운"`,
  `variant="오버그립"`). 특정 종목 전용이 아니라 모든 종목에 적용 가능한 범용 필드다.
- `MuscleGroup`은 신체 부위 기준 분류다. 기존 스펙의 `WorkoutTemplate.category`
  (`PUSH`/`PULL`/`LEGS`/`STRENGTH`, 운동 분할 방식 기준)와는 다른 축의 분류라
  이름이 겹치지 않도록 `Exercise.muscleGroup`으로 명명했다.
- `Exercise.scope`/`ownerId`의 일관성은 위 `init` 블록에서 검증한다 (`PERSONAL`은
  `ownerId` 필수, `GLOBAL`은 `null` 필수).

```kotlin
interface ExerciseRepository {
    fun findById(id: UUID): Exercise?
    fun findVisibleTo(userId: UUID): List<Exercise>              // GLOBAL + 본인 PERSONAL
    fun suggest(query: String, userId: UUID, limit: Int = 10): List<Exercise>  // trigram 유사도 정렬
    fun save(exercise: Exercise): Exercise
}
```

## API 설계

```
GET  /api/v1/exercises?muscleGroup={MG}    # 카탈로그 조회 (GLOBAL + 본인 PERSONAL), muscleGroup 필터 선택
GET  /api/v1/exercises/suggest?q={text}    # 유사 종목 제안 (trigram 유사도 정렬, 상위 10개 고정)
POST /api/v1/exercises                     # 개인 종목 등록 (scope=PERSONAL 자동, ownerId는 인증 컨텍스트에서)
```

```kotlin
data class ExerciseResponse(
    val id: UUID,
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val scope: ExerciseScope,
) {
    companion object {
        fun from(exercise: Exercise) = ExerciseResponse(
            id = exercise.id!!, name = exercise.name, variant = exercise.variant,
            muscleGroup = exercise.muscleGroup, type = exercise.type, scope = exercise.scope,
        )
    }
}

data class ExerciseCreateRequest(
    val name: String,
    val variant: String?,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
)
```

`POST`는 `UserController`의 `currentUserId()` 패턴을 재사용해 `SecurityContextHolder`에서
인증된 사용자 ID를 가져오고, `scope=PERSONAL`/`ownerId=currentUserId()`로 고정한다.
요청 바디는 `scope`/`ownerId`를 받지 않는다 (클라이언트가 GLOBAL 종목을 등록하거나
남의 `ownerId`를 지정할 수 없도록).

`suggest`의 결과 개수(10개)와 유사도 임계값은 클라이언트가 조절하지 않고 서버 내부
상수로 고정한다. 임계값 초기값은 `0.2`로 잡되(아래 쿼리 예시 참고), 구현 단계에서
"글로벌 시드 종목" 절의 실제 오타 사례들로 검증하며 조정한다.

이름 중복 검증은 하지 않는다 (Phase 1 스펙에서 이미 결정된 트레이드오프 — 정규화는
사용자가 `suggest` 결과를 보고 직접 판단).

## 영속성 / 마이그레이션

이 프로젝트에 Flyway를 처음 도입한다. `pg_trgm` 익스텐션 활성화와 GIN 트라이그램
인덱스처럼 Hibernate `ddl-auto`가 만들 수 없는 것들을 SQL로 직접 관리하기 위함이다.

**전제**: 로컬 개발 DB(`bali`)는 기존에 Hibernate `ddl-auto: update`로 생성된
`users` 테이블만 있는 상태다. 별도 baseline 처리 없이 로컬 DB를 한 번 초기화(drop &
recreate)하고 Flyway가 V1부터 전체 스키마를 관리하도록 한다 (로컬 개발용 더미
데이터라 보존 필요 없음). 이후 `spring.jpa.hibernate.ddl-auto`를 `update`에서
`validate`로 변경한다 — 스키마는 Flyway가 소유하고 Hibernate는 엔티티-테이블
매핑만 검증한다.

```
bali-infra/src/main/resources/db/migration/
├── V1__create_users_table.sql        # 기존 UserJpaEntity 매핑을 SQL로
├── V2__enable_pg_trgm.sql            # CREATE EXTENSION IF NOT EXISTS pg_trgm;
├── V3__create_exercises_table.sql    # exercises 테이블 + GIN 트라이그램 인덱스
└── V4__seed_global_exercises.sql     # 글로벌 시드 종목 INSERT (아래 목록 기준)
```

```sql
-- V3__create_exercises_table.sql
CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    variant VARCHAR(50),
    muscle_group VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    owner_id UUID
);
CREATE INDEX idx_exercises_name_trgm ON exercises USING GIN (name gin_trgm_ops);
```

`bali-infra/build.gradle.kts`에 `flyway-core` + `flyway-database-postgresql`
(Flyway 10+에서 Postgres 지원이 별도 아티팩트로 분리됨) 의존성을 추가한다.

`suggest` 조회는 JPQL로 표현할 수 없는 Postgres 전용 `similarity()` 함수를 쓰므로
네이티브 쿼리로 작성한다 (QueryDSL 도입은 검토했으나, 이 프로젝트에 QueryDSL 인프라가
전혀 없는 상태에서 쿼리 1개를 위해 애노테이션 프로세싱 툴체인을 새로 들이는 비용이
크다고 판단해 채택하지 않음):

```kotlin
@Query(
    value = """
        SELECT * FROM exercises
        WHERE (scope = 'GLOBAL' OR (scope = 'PERSONAL' AND owner_id = :userId))
          AND similarity(name, :query) > 0.2
        ORDER BY similarity(name, :query) DESC
        LIMIT :limit
    """,
    nativeQuery = true,
)
fun suggest(query: String, userId: UUID, limit: Int): List<ExerciseJpaEntity>
```

`findVisibleTo`는 Postgres 전용 함수가 필요 없으므로 JPQL로 작성한다:

```kotlin
@Query("SELECT e FROM ExerciseJpaEntity e WHERE e.scope = 'GLOBAL' OR (e.scope = 'PERSONAL' AND e.ownerId = :userId)")
fun findVisibleTo(userId: UUID): List<ExerciseJpaEntity>
```

## 에러 처리

- `Exercise` 생성 시 도메인 불변식 위반은 `IllegalArgumentException`으로 표출된다.
  지금까지 `bali-api`엔 전역 예외 처리기가 없었는데(도메인 검증이 API로 노출되는
  첫 케이스), `@RestControllerAdvice`를 추가해 `IllegalArgumentException` → 400 +
  `{"error": "..."}` JSON으로 매핑한다 (`SecurityConfig`의 401 응답 바디 형식과 동일).
- `muscleGroup`/`type`에 잘못된 enum 값이 오면 Jackson 역직렬화 단계에서 이미 400으로
  처리되므로 별도 코드가 필요 없다.
- 이름 중복 검증을 하지 않으므로 409 케이스는 없다.

## 테스트 전략

기존 컨벤션을 그대로 따른다.

- `bali-core`: Kotest `StringSpec` — `Exercise` 도메인 불변식 검증 (`UserTest.kt` 패턴)
- `bali-infra`: JUnit5 `@DataJpaTest` + 로컬 docker-compose Postgres (Mac Docker
  Desktop 호환성 문제로 Testcontainers 대신 이 방식 사용, `UserRepositoryAdapterTest.kt`와
  동일) — `save`/`findVisibleTo`/`suggest`(오타 사례 몇 개로 trigram 정렬이 실제
  동작하는지) 검증
- `bali-api`: JUnit5 `@SpringBootTest` + `MockMvc` — 3개 엔드포인트 통합 테스트,
  인증 컨텍스트에서 `ownerId`가 올바르게 세팅되는지 검증 (`UserControllerTest.kt` 패턴)

## 글로벌 시드 종목

사용자의 3년치 개인 기록에서 추출/정리한 기본 종목 목록이다. 실제 Flyway
`INSERT` 문 작성 시 이 목록을 기준으로 하며, 그립/자세 변형은 `variant` 값으로
같은 `name`에 여러 행을 추가한다 (예: `name="랫풀다운", variant="오버그립"`).

**BACK (15)**: 랫풀다운[오버그립/언더그립/클로즈그립/와이드그립/해머그립/프론트],
랙풀, 바벨로우[머신], 시티드로우[와이드/클로즈], 하이로우, 티바로우,
덤벨로우[원암], 케이블로우[원암], 암풀다운, 풀업, 롱풀, 레터럴로우, 로우로우(Low Row 머신),
머신로우, 벤트오버

**BICEPS (7)**: 바벨컬, 덤벨컬, 케이블컬, 해머컬, 리버스컬, 머신컬, 이지바컬

**CHEST (5)**: 벤치프레스[인클라인/디클라인/스미스], 덤벨프레스[인클라인],
머신체스트프레스[인클라인/디클라인], 체스트플라이[케이블/머신/버터플라이], 딥스

**TRICEPS (7)**: 케이블푸시다운, 시티드스컬크러셔, 케이블익스텐션, 오버헤드익스텐션,
라잉트라이셉스익스텐션, 트라이셉스익스텐션, 케이블킥백[원암]

**SHOULDER (13)**: 숄더프레스[머신/스미스], 밀리터리프레스[시티드], OHP[시티드],
비하인드넥프레스, 덤벨숄더프레스, 사이드레터럴레이즈[머신], 리어래터럴레이즈,
펙덱플라이[리버스], 프론트레이즈, 업라이트로우, 페이스풀, 힙허거, 케이블리어델트플라이

**LEGS (8)**: 스쿼트[원레그], 브이스쿼트, 핵스쿼트, 불가리안스플릿스쿼트,
레그프레스[원레그], 레그컬, 레그익스텐션, 런지[머신]

**ABS (2)**: 케이블크런치, 행잉레그레이즈

**CARDIO (2)**: 실내달리기, 실내걷기

기본 종목 59개 (variant 조합 포함 시 더 많음). 모두 `type=STRENGTH`이며 CARDIO
카테고리의 2개만 `type=CARDIO`다.

## 향후 고려사항 (이 문서 범위 밖)

- `variant`를 프론트엔드에서 어떻게 노출/선택할지(UI)는 별도 논의
- 개인 종목의 그립/부위 표기가 글로벌 시드와 다르게 자유롭게 들어올 수 있음 —
  `suggest` 기능이 이를 완화하지만, 완전히 막지는 않음 (의도된 트레이드오프)
