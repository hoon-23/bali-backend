# 개인 종목 수정/삭제 — 설계 문서

## 배경

2026-08-10 Phase 2 브레인스토밍(`[[project_next_step_exercise_catalog]]` 참고) 중 `ExerciseController`에도
기본 CRUD가 빠져있는 걸 발견했다: 개인 종목(PERSONAL) 등록(`POST`)만 있고 수정(`PUT`)·삭제(`DELETE`)가
없다. `PROGRESS.md` Phase 2 #3, 세션 삭제(#2) 바로 다음 순서다.

## 범위

### 이번 작업
- `PUT /api/v1/exercises/{id}` — `name`/`variant`/`muscleGroup`만 수정. 본인 소유 PERSONAL 종목만 대상
- `DELETE /api/v1/exercises/{id}` — 본인 소유 PERSONAL 종목만 대상, 활성 템플릿이 참조 중이면 409

### 범위 밖
- GLOBAL(시드) 종목 수정/삭제 — 관리자 기능이 아니며, 이번 작업 대상이 아님. `findOwnedPersonalOrNull`
  헬퍼가 GLOBAL을 항상 404로 처리해 자연스럽게 막힌다
- `type`(STRENGTH/CARDIO) 수정 — 아래 "설계 결정" 참고, 의도적으로 제외
- `session_logs` 참조 체크 — 아래 "설계 결정" 참고, 의도적으로 제외

## 설계 결정

**PUT에서 `type`을 뺀 이유:** 이미 `template_items`에 저장된 항목은 등록 당시 종목의 `type`에 맞는
target 필드 모양(STRENGTH면 sets/reps/weight, CARDIO면 duration/pace)으로 저장돼 있다. 나중에 종목의
`type`만 바꾸면, 그 템플릿으로 새 세션을 만들 때(`SessionController.create`가 `exercise.type` 기준으로
`SessionLog.create()`를 호출) 저장된 target 필드와 새 type이 어긋나 `require` 검증에 걸려 에러가 난다 —
수정 시점과 한참 떨어진 곳에서 터지는 지연 폭탄이다. 그래서 `type`은 생성 후 불변으로 고정한다. 타입을
잘못 등록했으면 삭제 후 재등록하는 편이 안전하다.

**DELETE에서 `template_items`(활성 템플릿만)만 체크하고 `session_logs`는 체크하지 않는 이유:**
`session_logs`는 이미 끝난 과거 운동 기록이라 참조된 채로 남아도 무해하다(조회 시 `exerciseId`를 그대로
내려줄 뿐 재조회하지 않음 — `SessionResponse.from` 참고). 반면 `template_items`는 재사용되는 계획이라,
참조된 종목을 지우면 나중에 그 템플릿으로 새 세션을 만들 때 에러가 난다(위와 같은 종류의 지연 폭탄).
`session_logs`까지 막으면 한 번이라도 기록한 개인 종목을 영원히 못 지우게 되는 나쁜 UX가 되므로,
실제로 문제되는 참조(활성 템플릿)만 막는다. 소프트 삭제된 템플릿은 `WorkoutTemplateRepository.findById`가
이미 `null`을 반환해 새 세션 생성에 쓰일 수 없으므로(`deleted = false`인 템플릿만 대상), 참조 체크에서
제외한다.

## API 설계

```
PUT /api/v1/exercises/{id}
Body: {"name": string, "variant": string?, "muscleGroup": MuscleGroup}
→ 200 OK + ExerciseResponse (성공)
→ 400 Bad Request (name 공백/255자 초과 — 기존 ExerciseCreateRequest와 동일한 검증)
→ 404 Not Found (없거나, GLOBAL이거나, 다른 유저 소유)

DELETE /api/v1/exercises/{id}
→ 204 No Content (성공)
→ 404 Not Found (없거나, GLOBAL이거나, 다른 유저 소유)
→ 409 Conflict (활성 템플릿의 template_items가 참조 중)
```

```kotlin
// name/variant/muscleGroup만 수정 가능. type은 불변이라 요청 바디에 없음
data class ExerciseUpdateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:Size(max = 255) val variant: String?,
    val muscleGroup: MuscleGroup,
)
```

```kotlin
@PutMapping("/{id}")
fun update(@PathVariable id: UUID, @Valid @RequestBody request: ExerciseUpdateRequest): ResponseEntity<ExerciseResponse> {
    val existing = findOwnedPersonalOrNull(id) ?: return ResponseEntity.notFound().build()
    val saved = exerciseRepository.save(existing.copy(name = request.name, variant = request.variant, muscleGroup = request.muscleGroup))
    return ResponseEntity.ok(ExerciseResponse.from(saved))
}

@DeleteMapping("/{id}")
fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
    val existing = findOwnedPersonalOrNull(id) ?: return ResponseEntity.notFound().build()
    if (templateRepository.existsActiveReferenceToExercise(existing.id!!)) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build()
    }
    exerciseRepository.deleteById(id)
    return ResponseEntity.noContent().build()
}

// id로 조회한 종목이 현재 인증 사용자 소유의 PERSONAL 종목일 때만 반환 (GLOBAL/타인 소유는 404 취급)
private fun findOwnedPersonalOrNull(id: UUID): Exercise? {
    val exercise = exerciseRepository.findById(id) ?: return null
    return if (exercise.scope == ExerciseScope.PERSONAL && exercise.ownerId == currentUserId()) exercise else null
}
```

기존 `findVisibleTo(id, userId)`는 GLOBAL도 "보이는 것"으로 포함해서 리턴하므로 여기서는 쓰지 않고,
GLOBAL/타인 소유를 전부 걸러내는 `findOwnedPersonalOrNull`을 새로 둔다 (다른 컨트롤러의
`findOwnedOrNull` 패턴과 동일선상, `ExerciseController` 전용 이름).

## 리포지토리 변경

**`ExerciseRepository`(core 포트)에 추가:**
```kotlin
fun deleteById(id: UUID)
```

**`WorkoutTemplateRepository`(core 포트)에 추가:**
```kotlin
// exerciseId가 소프트 삭제되지 않은 템플릿의 items에서 참조되고 있는지 확인
fun existsActiveReferenceToExercise(exerciseId: UUID): Boolean
```

**어댑터 구현:** 세션 삭제(Phase 2 #2) 작업에서 확인했듯, `JpaRepository.deleteById`(entity 기반
find+remove)는 같은 트랜잭션 안에서 바로 이어지는 조회가 flush 지연으로 스테일 데이터를 볼 수 있다.
`ExerciseJpaRepository`에도 동일하게 `@Modifying(clearAutomatically = true) @Query(DELETE ...)` bulk
delete 패턴을 적용한다.

```kotlin
// ExerciseJpaRepository에 추가
@Modifying(clearAutomatically = true)
@Query("DELETE FROM ExerciseJpaEntity e WHERE e.id = :id")
fun deleteExerciseById(@Param("id") id: UUID)
```

```kotlin
// TemplateItemJpaRepository에 추가 (네이티브 쿼리 — template_items/templates 간
// JPA 연관관계가 없어 조인이 필요하므로 명시적 SQL이 JPQL ad-hoc join보다 명확함)
@Query(
    value = """
        SELECT EXISTS (
            SELECT 1 FROM template_items ti
            JOIN templates t ON ti.template_id = t.id
            WHERE ti.exercise_id = :exerciseId AND t.deleted = false
        )
    """,
    nativeQuery = true,
)
fun existsActiveReferenceToExercise(@Param("exerciseId") exerciseId: UUID): Boolean
```

`WorkoutTemplateRepositoryAdapter.existsActiveReferenceToExercise`는 위 `itemJpaRepository` 메서드에
그대로 위임한다. `exercises` 테이블은 `template_items`/`session_logs`로부터 DB FK 제약을 받지 않으므로
(애그리거트 경계를 넘는 FK는 제약 없음 — 기존 컨벤션) 하드 삭제에 별도 cascade 처리가 필요 없다.

## 테스트 전략

- `ExerciseControllerTest`:
  - 본인 PERSONAL 종목 수정 성공(200, 필드 반영 확인)
  - GLOBAL 종목 수정 시도 → 404
  - 타인 소유 PERSONAL 종목 수정 시도 → 404
  - name 공백으로 수정 시도 → 400
  - 참조 없는 본인 PERSONAL 종목 삭제 → 204, 이후 목록 조회에서 사라짐
  - 활성 템플릿이 참조 중인 종목 삭제 시도 → 409, 실제로 삭제되지 않음(목록에 남아있음)
  - GLOBAL/타인 소유 종목 삭제 시도 → 404
- `ExerciseRepositoryAdapterTest`: `deleteById` 호출 후 `findById`가 null을 반환하는지(캐시 스테일
  회귀 방지)
- `WorkoutTemplateRepositoryAdapterTest`: `existsActiveReferenceToExercise` — 활성 템플릿이 참조하면
  true, 소프트 삭제된 템플릿만 참조하면 false, 아무도 참조 안 하면 false

## 향후 고려사항

없음 — 범위가 작고 독립적이라 후속 작업으로 넘길 게 딱히 없다.
