# 세트 타이머 — 설계 문서

## 배경

`PROGRESS.md` Phase 2 #6. 2026-08-07 주간 배치 분석 설계 중 STRENGTH 종목은 정확한 운동
소요시간을 계산할 필드가 없다는 갭이 발견되어(당시엔 10~15분/종목 추정치로 대체), 세트별
시작/종료 시각을 기록하는 기능으로 별도 스코프됐다. 이후 2026-08-10 브레인스토밍에서 핵심
설계(세트 단위 스톱워치, no pause, 배치 제출, JSONB 저장)가 확정됐고, 2026-08-18
`bali-frontend`가 운동 진행 화면(타이머/세트 기록)을 실제 API 없이는 만들 수 없는 지점까지
와서 blocking dependency로 승격, 이번에 구현한다.

## 범위

### 이번 작업
- `SessionLog`에 세트별 시작/종료 시각 리스트(`setTimings`) 필드 추가
- `session_logs` 테이블에 `set_timings JSONB` 컬럼 추가
- 기존 `PATCH /api/v1/sessions/{id}/logs/{logId}`를 확장해 `setTimings`를 함께 기록
- `SessionLogResponse`에 `setTimings` 원본 리스트 포함

### 범위 밖
- **휴식시간/총 운동시간 계산** — 백엔드는 원본 타임스탬프만 저장·반환한다. 계산은
  `bali-frontend`가 받은 타임스탬프로 직접 수행한다(서버 부하 최소화 원칙). 이번 스코프에서
  결정, `bali-frontend` 세션에도 전달 완료.
- **세트 순서/겹침/미래시각 검증** — 배치 제출(운동 끝난 뒤 한 번에 전송) 모델이라, 엄격
  검증으로 400을 반환하면 사용자가 방금 끝낸 운동 기록 자체를 통째로 잃는다(다시 잴 수 없는
  과거 데이터). 앱 백그라운드 전환, 기기 시계 오차 등으로 자연스럽게 발생 가능한 잡음까지
  걸러내려 하지 않는다. 최소 검증(세트별 `endedAt > startedAt`)만 수행 — 상세는 "검증 규칙" 참고.
- **주간 분석의 총 운동시간 추정치 교체** — Phase 2 #8(통계 대시보드) 또는 별도 후속 작업에서
  실측치로 전환. 이번 스코프는 기록 기능까지만.
- 세트 시작/종료 버튼 UX(일시정지·재개 토글 등)는 `bali-frontend` 쪽 결정 사항, 데이터 모델과
  무관하므로 이번 스펙에 포함하지 않는다.

## 데이터 모델

`bali-core`에 새 타입 추가:

```kotlin
// com.bali.core.session
data class SetTiming(val setIndex: Int, val startedAt: Instant, val endedAt: Instant)
```

`SessionLog`에 필드 추가:

```kotlin
data class SessionLog(
    // ...기존 필드
    val setTimings: List<SetTiming>? = null,
)
```

검증은 기존 `validateActualFields`와 분리된 별도 함수로 (관심사 분리):

```kotlin
// setTimings가 exerciseType/타임스탬프 제약을 만족하는지 검증
fun validateSetTimings(exerciseType: ExerciseType, setTimings: List<SetTiming>?) {
    if (exerciseType == ExerciseType.CARDIO) {
        require(setTimings == null) { "CARDIO exercise must not have setTimings" }
    }
    setTimings?.forEach { st ->
        require(st.endedAt > st.startedAt) { "setTiming.endedAt must be after startedAt (setIndex=${st.setIndex})" }
    }
}
```

`session_logs` 마이그레이션 (`V11__add_set_timings_to_session_logs.sql`):

```sql
ALTER TABLE session_logs ADD COLUMN set_timings JSONB;
```

`SessionLogJpaEntity`에 `WeeklyAnalysisJpaEntity.summary`와 동일한 패턴으로 컬럼 추가:

```kotlin
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
var setTimings: String? = null
```

**구현 시 주의할 기존 갭:** `WorkoutSessionRepositoryAdapter`가 쓸 JSON 직렬화용
`ObjectMapper`는 `WeeklyAnalysisRepositoryAdapter`처럼 `jacksonObjectMapper()`로 새로
만들어야 하는데(자체 어댑터 클래스 내부 필드), 이 함수는 `Instant` 직렬화 모듈
(`jackson-datatype-jsr310`)을 자동 등록하지 않는다. `bali-infra/build.gradle.kts`에
`jackson-datatype-jsr310` 의존성을 추가하고, `JavaTimeModule()`을 등록한 뒤
`SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`를 비활성화해 `Instant`가 epoch 배열이
아닌 ISO-8601 문자열로 저장되게 한다. (API 계층의 `ObjectMapper`는 Spring Boot
autoconfigure가 이미 jsr310을 등록해두므로 이 문제가 없다 — 영향받는 건 인프라 계층의
수동 생성 ObjectMapper뿐이다.)

`WorkoutSessionRepository` 포트의 `recordActual`에 파라미터 추가:

```kotlin
fun recordActual(
    logId: UUID,
    completed: Boolean?,
    actualSets: Int?, actualReps: Int?, actualWeight: BigDecimal?,
    actualDurationSeconds: Int?, actualPace: String?,
    setTimings: List<SetTiming>?,   // null=유지, non-null=전체 교체
): SessionLog?
```

`setTimings`는 다른 필드들과 달리 null=유지는 같지만, non-null이면 기존 리스트를 부분
병합이 아니라 **통째로 교체**한다 — "운동 완료 시 전체 세트 리스트를 한 번에 제출"하는
배치 제출 모델과 일치.

## 검증 규칙

| 규칙 | 위반 시 |
|---|---|
| CARDIO 종목인데 `setTimings`가 non-null | 400 |
| 세트의 `endedAt <= startedAt` | 400 |
| `setIndex` 순서/연속성 | 검증 안 함 |
| 세트 간 시간 겹침 | 검증 안 함 |
| 미래 시각 | 검증 안 함 |

## API 설계

```
PATCH /api/v1/sessions/{id}/logs/{logId}
Body:
{
  "completed": true,
  "actualSets": 3, "actualReps": 10, "actualWeight": 60.0,
  "setTimings": [
    { "setIndex": 0, "startedAt": "2026-08-18T10:00:00Z", "endedAt": "2026-08-18T10:00:45Z" },
    { "setIndex": 1, "startedAt": "2026-08-18T10:02:10Z", "endedAt": "2026-08-18T10:02:58Z" },
    { "setIndex": 2, "startedAt": "2026-08-18T10:04:30Z", "endedAt": "2026-08-18T10:05:20Z" }
  ]
}
→ 200 OK (갱신된 SessionLogResponse, setTimings 포함)
→ 400 Bad Request (검증 규칙 위반 시)
```

`SessionLogPatchRequest`/`SessionLogResponse`에 대칭적으로 `setTimings:
List<SetTimingRequest>?` / `List<SetTimingResponse>?` 추가 (`SetTimingRequest`/
`SetTimingResponse`는 각각 `setIndex`, `startedAt`, `endedAt`을 그대로 담는 순수 변환
DTO — 계산 로직 없음).

## 테스트 전략

- `SessionLogTest`: `validateSetTimings` — CARDIO+non-null 거부, `endedAt<=startedAt`
  거부, STRENGTH+정상 케이스 통과, `setTimings=null` 통과(선택적 필드이므로)
- `WorkoutSessionRepositoryAdapterTest`: `setTimings` JSON round-trip 저장/조회
  (`Instant` 직렬화가 ISO-8601 문자열로 왕복되는지 확인 포함), `setTimings=null` 전달 시
  기존 값 유지
- `SessionControllerTest`:
  - PATCH로 `setTimings` 저장 후 GET 응답에 포함되는지
  - CARDIO 종목 로그에 `setTimings` 포함 PATCH 시 400
  - `endedAt<=startedAt`인 세트 포함 PATCH 시 400
  - `setTimings` 없이 PATCH(기존 필드만) 시 기존 `setTimings` 유지되는지
  - cross-user 케이스는 기존 `patchLog` 테스트 패턴 재사용

## 향후 고려사항

- Phase 2 #8(통계 대시보드) 또는 주간 배치 분석에서 STRENGTH 총 운동시간 추정치(10~15분/종목)를
  `setTimings` 실측치로 교체하는 건 별도 작업
- 세트 시작/종료 버튼의 정확한 UX(일시정지/재개 토글 등)는 `bali-frontend` 쪽에서 결정, 이
  데이터 모델과 무관하게 자유롭게 매핑 가능
