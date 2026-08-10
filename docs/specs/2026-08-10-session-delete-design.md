# 세션 삭제 — 설계 문서

## 배경

2026-08-10 Phase 2 브레인스토밍 중 `SessionController`를 다시 확인하다 기본 CRUD가 하나
빠져있는 걸 발견했다: 생성/조회(목록·단건)/아이템 패치는 있는데 세션 전체를 지우는
`DELETE`가 없다. 잘못 기록했거나 더 이상 필요 없는 세션을 지울 방법이 없는 상태다.

Phase 2 순서를 "기본적인 앱 사용 서비스 흐름" 기준으로 재정렬하면서, 이 항목(+ 개인 종목
수정/삭제)이 세션 CRUD를 완비하는 가장 기본적인 작업으로 1순위가 됐다
(`[[project_next_step_exercise_catalog]]`, `PROGRESS.md` Phase 2 #2 참고).

## 범위

### 이번 작업
- `DELETE /api/v1/sessions/{id}` 엔드포인트 추가
- 본인 소유 세션만 삭제 가능 (기존 `findOwnedOrNull` 재사용), 없거나 남의 세션이면 404
- 하드 삭제. `session_logs.session_id`가 이미 `ON DELETE CASCADE`로 걸려있어 세션 row
  삭제만으로 소속 logs가 함께 제거된다 (`V6__create_templates_and_sessions_tables.sql`)

### 범위 밖
- 개인 종목 수정/삭제 (`PUT`/`DELETE /api/v1/exercises/{id}`) — Phase 2 #3, 별도 스펙으로 진행
- 소프트 삭제 — `templates`와 달리 `sessions`는 다른 엔티티가 참조하지 않아 보존할 이유가
  없다. `sessions` 테이블에 `deleted` 컬럼 자체도 없다
- 삭제된 세션이 이미 생성된 주간 분석 리포트에 미치는 영향 처리 — `WeeklyAnalysis`는 배치
  시점의 스냅샷이라 사후 세션 삭제와 무관하게 그대로 유지되는 게 기존 배치 설계와 일관됨
  (재계산 트리거 없음)

## API 설계

```
DELETE /api/v1/sessions/{id}
→ 204 No Content (성공)
→ 404 Not Found (없거나 다른 유저 소유)
```

`TemplateController.delete`와 동일한 모양이되, `softDelete` 대신 실제 삭제를 호출한다.

```kotlin
@DeleteMapping("/{id}")
fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
    findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
    sessionRepository.deleteById(id)
    return ResponseEntity.noContent().build()
}
```

## 리포지토리 변경

`WorkoutSessionRepository`(core 포트)에 메서드 추가:

```kotlin
fun deleteById(id: UUID)
```

`WorkoutSessionRepositoryAdapter` 구현은 DB cascade에 위임하므로 한 줄:

```kotlin
@Transactional
override fun deleteById(id: UUID) {
    sessionJpaRepository.deleteById(id)
}
```

## 테스트 전략

- `SessionControllerTest`: 본인 세션 삭제 시 204, 이후 조회 시 404가 되는지 확인
- 다른 유저의 세션 삭제 시도 시 404 (소유권 검증)
- 존재하지 않는 id 삭제 시도 시 404
- `WorkoutSessionRepositoryAdapterTest`: 세션 삭제 후 그 소속 `session_logs`도 함께
  삭제되는지 확인 (cascade 검증)

## 향후 고려사항

없음 — 범위가 작고 독립적이라 후속 작업으로 넘길 게 딱히 없다.
