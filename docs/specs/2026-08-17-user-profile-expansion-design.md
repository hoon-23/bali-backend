# 사용자 프로필 확장 — 설계 문서

## 배경

2026-08-17, `bali-frontend` 스캐폴딩을 앞두고 Claude Design 목업(로그인/프로필 화면)을
기존 백엔드와 대조하는 브레인스토밍 중 발견: `User` 도메인(`bali-core/.../user/User.kt`)에
`email` 외 표시용 필드가 전혀 없어서, 목업의 프로필 화면(닉네임, 연속운동일 표시)을 구현할
방법이 없다. 소셜 로그인 provider 중 Apple은 이메일 자체가 `{providerId}@apple.bali.internal`
같은 placeholder일 수 있어(`SocialLoginService.placeholderEmail`), email을 표시명으로 대체하는
것도 불가능하다.

`PROGRESS.md` Phase 2 #5로 편입, 로그인/프로필 프론트 작업의 선행 조건이라 세션
정확도(세트 타이머 등) 항목들보다 앞에 배치했다.

## 범위

### 이번 작업
- `User` 도메인에 `nickname`(표시명), `weeklyGoalSessions`(주간 목표 운동 횟수) 필드 추가
- 가입 시 닉네임 자동 생성 (형용사+명사+숫자 조합)
- `PATCH /api/v1/users/me` 신규 — 닉네임/주간 목표 수정
- `GET /api/v1/users/me` 응답에 `nickname`, `weeklyGoalSessions`, `consecutiveDays`(연속운동일,
  계산값) 추가

### 범위 밖
- **레벨/경험치 게이미피케이션** — `PROGRESS.md`에 "미정 후보"로만 기록된 완전 신규 아이디어,
  스코프 결정 전까지 구현하지 않는다
- **"오늘의 운동" 추천 카드** — 마찬가지로 미정 후보, 이번 작업과 무관
- **통계 대시보드(일별 세분화, 전체 누적 통계)** — Phase 2 #8, 별도 스펙
- 프로필 이미지 — 목업에 있지만 이번 스코프에 없음(요청받지 않음, YAGNI)

## 데이터 모델

`users` 테이블에 컬럼 2개 추가:

```sql
ALTER TABLE users ADD COLUMN nickname VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN weekly_goal_sessions INT NOT NULL DEFAULT 3;
```

- `nickname`: unique 제약 없음 (표시용, 중복 허용). 신규 가입자는 애플리케이션 코드가 즉시
  채우므로 `DEFAULT ''`는 기존 행 백필용 임시값일 뿐, 신규 insert 시점엔 항상 실제 값이 들어간다
- `weekly_goal_sessions`: 기본 3, 사용자가 2/3/5 등으로 변경 가능

**기존 행 백필:** 이 프로젝트는 아직 실사용자가 없는 개발 단계([[project_pipa_compliance_gaps]]
참고 — PIPA 대응도 미완료 상태)라, Flyway Java 콜백 같은 정교한 백필 로직은 오버엔지니어링으로
판단했다. 마이그레이션에서 SQL로 간단히 채운다:

```sql
UPDATE users SET nickname = '사용자' || substr(id::text, 1, 4) WHERE nickname = '';
```

정식 랜덤 생성기(형용사+명사 조합)는 Kotlin 코드에만 존재하며, 신규 가입 시에만 적용된다.
기존 행은 이 임시 이름을 유지하다가 사용자가 직접 `PATCH`로 바꾸면 그만이다.

`User` 도메인 모델:

```kotlin
data class User(
    val id: UUID?,
    val email: String,
    val nickname: String,
    val weeklyGoalSessions: Int,
    val provider: AuthProvider,
    val providerId: String,
    val status: UserStatus,
    val createdAt: Instant,
) {
    fun withdraw(): User = copy(status = UserStatus.WITHDRAWN)
    // 닉네임/주간 목표 수정. 둘 다 optional — null이면 기존 값 유지
    fun updateProfile(nickname: String?, weeklyGoalSessions: Int?): User =
        copy(
            nickname = nickname ?: this.nickname,
            weeklyGoalSessions = weeklyGoalSessions ?: this.weeklyGoalSessions,
        )
}
```

## 닉네임 자동 생성

`SocialLoginService.createUser()`에서 User 생성 시 호출. 새 객체 `NicknameGenerator`
(`bali-core` 또는 `bali-api`, 순수 함수라 core에 둔다)로 분리:

```kotlin
object NicknameGenerator {
    private val ADJECTIVES = listOf("행복한", "용감한", "즐거운", "씩씩한", "차분한", ...)
    private val NOUNS = listOf("옥수수", "사자", "호랑이", "감자", "다람쥐", ...)

    fun generate(userId: UUID): String {
        val adjective = ADJECTIVES.random()
        val noun = NOUNS.random()
        val number = Math.abs(userId.hashCode()) % 100
        return "$adjective$noun${number.toString().padStart(2, '0')}"
    }
}
```

- 숫자는 uniqueness 보장용이 아니라 장식 목적. `user.id`(UUID) 자체엔 숫자만 뽑아낼 방법이
  없으므로(랜덤 hex 문자열) `hashCode() % 100`으로 단순화 — 로직 복잡도를 낮추는 게 우선이라는
  피드백 반영
- 형용사/명사 목록은 최소 10~15개씩만 채워 시작 (조합 100+ 가지면 충분, 나중에 부족하면
  추가하는 게 YAGNI에 맞음)

## 스트릭(연속운동일) 계산

**저장하지 않고 매 조회 시 계산.** `WeeklyAnalysis`처럼 배치 사전계산이 필요할 만큼 무거운
연산이 아니고(최근 1년 세션 날짜 조회 후 메모리에서 순회), 저장하면 세션 완료/삭제 시마다
재계산 트리거를 관리해야 하는 부담이 생긴다.

**"운동한 날" 정의:** 그 날짜의 `WorkoutSession` 소속 `SessionLog` 중 `completed = true`가
하나라도 있는 날. 세션만 생성하고 아무것도 완료 안 했으면 불인정 — target만 세팅된 빈 세션은
"운동함"이 아니라는 기존 도메인 관점(`SessionLog.completed`가 존재하는 이유)과 일치.

**공백 허용:** 오늘 아직 기록이 없어도 스트릭은 안 끊긴 걸로 처리한다 — 최근 활동일이 오늘 또는
어제면 거기서부터 역산, 그보다 오래됐으면 0. (하루 걸러뛰면 바로 0이 되는 건 사용자 경험상
가혹하다는 일반적 판단 — Duolingo류 스트릭 앱의 통상 패턴)

**계산 범위:** 무한정 과거까지 조회하지 않고 최근 400일로 제한(주 3회 기준으로도 넉넉한
범위, 쿼리 비용 상한선).

`bali-core`에 순수 함수로 분리(`WeeklyStatsCalculator`와 같은 패턴):

```kotlin
object StreakCalculator {
    fun calculate(activeDates: Set<LocalDate>, today: LocalDate): Int {
        val mostRecent = activeDates.maxOrNull() ?: return 0
        if (mostRecent.isBefore(today.minusDays(1))) return 0

        var streak = 0
        var cursor = mostRecent
        while (cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
```

`WorkoutSessionRepository`(core 포트)에 메서드 추가:

```kotlin
// 완료된 로그가 하나 이상 있는 날짜 집합을 조회 (최근 400일 범위 내)
fun findActiveDates(userId: UUID, since: LocalDate): Set<LocalDate>
```

`WorkoutSessionRepositoryAdapter` 구현은 JPQL로 DB 단에서 distinct+필터 (전체 세션 그래프를
메모리에 안 실어도 되도록):

```kotlin
override fun findActiveDates(userId: UUID, since: LocalDate): Set<LocalDate> {
    return sessionJpaRepository.findActiveDates(userId, since).toSet()
}
```

```kotlin
@Query("""
    SELECT DISTINCT s.date FROM WorkoutSessionJpaEntity s
    JOIN s.logs l
    WHERE s.userId = :userId AND s.date >= :since AND l.completed = true
""")
fun findActiveDates(userId: UUID, since: LocalDate): List<LocalDate>
```

## API 설계

```
GET /api/v1/users/me
→ 200 OK
{
  "id": "...",
  "email": "...",
  "nickname": "행복한옥수수07",
  "weeklyGoalSessions": 3,
  "consecutiveDays": 12,
  "status": "ACTIVE"
}
```

```
PATCH /api/v1/users/me
Body: { "nickname": "새닉네임", "weeklyGoalSessions": 5 }   // 둘 다 optional
→ 200 OK (수정된 UserResponse)
→ 400 Bad Request (nickname 공백/과도한 길이, weeklyGoalSessions 범위 밖)
```

**검증 규칙:**
- `nickname`: trim 후 1~20자 (공백만으로 이루어진 값 거부, 단어 사이 공백 자체는 허용 — 구체적
  금칙어/특수문자 필터는 이번 스코프 밖, YAGNI, 필요성이 확인되면 추가)
- `weeklyGoalSessions`: 1~7 범위 (주 1회 미만이거나 8회 이상은 의미 없음)

```kotlin
@PatchMapping("/me")
fun updateProfile(@Valid @RequestBody request: UserUpdateRequest): ResponseEntity<UserResponse> {
    val user = userRepository.findById(currentUserId())
        ?: return ResponseEntity.notFound().build()
    val updated = userRepository.save(user.updateProfile(request.nickname, request.weeklyGoalSessions))
    return ResponseEntity.ok(UserResponse.from(updated, consecutiveDays = calculateStreak(updated.id!!)))
}
```

`UserResponse.from`은 `consecutiveDays`를 인자로 받도록 확장(계산은 컨트롤러가 리포지토리 통해
수행 후 주입 — DTO는 순수 변환만 담당하는 기존 패턴 유지).

## 테스트 전략

- `UserControllerTest`:
  - `GET /me` 응답에 nickname/weeklyGoalSessions/consecutiveDays 포함 확인
  - `PATCH /me`로 nickname만 변경 시 weeklyGoalSessions 유지되는지 (부분 업데이트)
  - `PATCH /me`로 weeklyGoalSessions 범위 밖(0, 8) 요청 시 400
  - `PATCH /me`로 nickname 공백/21자 이상 요청 시 400
- `SocialLoginServiceTest`: 신규 가입 시 nickname이 채워지는지 (빈 문자열 아님)
- `NicknameGeneratorTest` (`bali-core`): 같은 userId로 여러 번 호출해도 숫자 부분이 항상
  같은지(hashCode 기반이라 결정적이어야 함 — 형용사/명사는 랜덤이라 매번 다를 수 있음)
- `StreakCalculatorTest` (`bali-core`, 순수 함수라 단위 테스트로 충분):
  - 오늘 포함 연속 N일 → N
  - 어제까지만 있고 오늘 없음 → 어제까지의 연속일수 (안 끊김)
  - 그제 이전이 마지막 활동일 → 0
  - 활동 없음(빈 집합) → 0
  - 중간에 공백 있는 날짜들 → 최근 연속 구간만 카운트
- `WorkoutSessionRepositoryAdapterTest`: `findActiveDates`가 completed=false인 로그의 날짜는
  제외하는지, since 범위 밖 날짜는 제외하는지

## 향후 고려사항

- 닉네임 형용사/명사 목록이 너무 작아 조합이 겹친다는 피드백이 오면 목록 확장 (지금은 최소
  스타트)
- 레벨/경험치, "오늘의 운동" 추천은 이번 스코프에서 의도적으로 제외 — 별도 브레인스토밍 필요
